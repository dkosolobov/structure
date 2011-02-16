package ibis.structure;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntLongHashMap;
import gnu.trove.TIntIterator;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/** The core algorithms for sat solving. */
public final class Solver {
  private static final Logger logger = Logger.getLogger(Solver.class);

  /** Number of variables. */
  public int numVariables;
  /** Set of true literals discovered. */
  public TouchSet units;
  /** Equalities between literals. */
  public int[] proxies;
  /** The implication graph. */
  public ImplicationsGraph graph;
  /** Watchlists. */
  public WatchLists watchLists;
  /** Units queue. */
  private TIntArrayList unitsQueue;

  /** Constructor. */
  public Solver(final Skeleton instance) {
    numVariables = instance.numVariables;
    units = new TouchSet(numVariables);
    graph = new ImplicationsGraph(numVariables);
    watchLists = new WatchLists(numVariables, instance.formula);
    unitsQueue = new TIntArrayList();

    proxies = new int[2 * numVariables + 1];
    for (int u = -numVariables; u <= numVariables; ++u) {
      proxies[u + numVariables] = u;
    }

    if (Configure.verbose) {
      System.err.print(".");
      System.err.flush();
    }
    // logger.info("******************* SOLVING *****************************************");
    // logger.info("I am " + instance);
  }

  /** Returns true if literal u is already assigned. */
  public boolean isLiteralAssigned(final int u) {
    assert u != 0 && -numVariables <= u && u <= numVariables;
    return proxy(u) != u || units.contains(u) || units.contains(-u);
  }

  /** Returns true if variable u is missing. */
  public boolean isVariableMissing(final int u) {
    assert var(u) == u;
    return proxy(u) == u
        && !units.contains(u) && !units.contains(-u)
        && graph.edges(u).isEmpty() && graph.edges(-u).isEmpty()
        && watchLists.get(u).isEmpty() && watchLists.get(-u).isEmpty();
  }

  /**
   * Recursively finds the proxy of u.
   * The returned literal doesn't have any proxy.
   */
  private int proxy(final int u) {
    assert u != 0 : "0 is not a valid literal";
    int u_ = u + numVariables;
    if (u != proxies[u_]) {
      proxies[u_] = proxy(proxies[u_]);
      return proxies[u_];
    }
    return u;
  }

  /** Returns a string representation of stored instance. */
  /*
  public String toString() {
    return skeleton().toString();
  }
  */

  /** Queues a new assigned unit. */
  public void queueUnit(final int u) {
    unitsQueue.add(u);
  }

  /** Adds implication -u &rarr; v. */
  public void addBinary(final int u, final int v) {
    // logger.info("new binary " + u + " " + v);
    // logger.info("new binary " + proxies[u + numVariables] + " " + proxies[v + numVariables]);
    // logger.info("units " + units.contains(u) + " " + units.contains(v));
    assert !isLiteralAssigned(u) : "First literal " + u + " is assinged";
    assert !isLiteralAssigned(v) : "Second literal " + v + " is assinged";
    graph.add(-u, v);
  }

  /**
   * Returns current (simplified) instance.
   *
   * Includes units and equivalent literals.
   *
   * @return a skeleton with the instance.
   */
  /*
  public Skeleton skeleton() {
    Skeleton instance = new Skeleton(numVariables);


    // Appends the implications graph and clauses
    instance.formula = compact(watchLists.formula());
    instance.formula.add(graph.skeleton().formula.toNativeArray());

    // Appends units and equivalent relations
    for (int literal = -numVariables; literal <= numVariables; ++literal) {
      if (literal != 0) {
        int proxy = proxy(literal);
        if (units.contains(proxy)) {
          instance.formula.add(encode(1, OR));
          instance.formula.add(literal);
        } else if (literal != proxy && !units.contains(-proxy)) {
          // literal and proxy are equivalent,
          // but proxy is not assigned
          instance.formula.add(encode(2, OR));
          instance.formula.add(literal);
          instance.formula.add(-proxy);
        }
      }
    }

    return instance;
  }
    */


  /**
   * Returns a literal to branch on.
   *
   * @param branch literal to branch on. 0 for no branching.
   * @return solution or simplified instance.
   */
  public Solution solve(final int branch) {
    try {
      if (branch != 0) {
        queueUnit(branch);
      }

      watchLists.build();
      simplify(branch == 0);
    } catch (ContradictionException e) {
      // logger.info("Contradiction at ", e);
      return Solution.unsatisfiable();
    }

    // Checks if all clauses were removed.
    boolean empty = !(new ClauseIterator(watchLists.formula())).hasNext();
    if (empty) {
      // Solves the remaining 2SAT encoded in the implication graph
      try {
        // Collapses strongly connected components and removes contradictions.
        // No new binary clause is created because there are no clauses
        // of longer length.

        renameEquivalentLiterals();
        propagate();

        TIntArrayList assigned = new TIntArrayList();
        for (int u = 1; u <= numVariables; ++u) {
          if (isLiteralAssigned(u)) {
            assigned.add(u);
          }
        }

        units.add(graph.solve(assigned));
      } catch (ContradictionException e) {
        // logger.info("Contradiction at ", e);
        return Solution.unsatisfiable();
      }
    }

    // Assigns literals with assigned proxies.
    for (int literal = -numVariables; literal <= numVariables; ++literal) {
      if (literal == 0) {
        continue;
      }
      int proxy = proxy(literal);
      if (literal == proxy) {
        continue;
      }

      if (units.contains(proxy)) {
        units.add(literal);
        proxies[literal + numVariables] = literal;
        proxies[-literal + numVariables] = -literal;
      }
    }

    return empty ? Solution.satisfiable(units.toArray()) : Solution.unknown();
  }

  public Core core() {
    assert unitsQueue.isEmpty();

    TIntArrayList formula = watchLists.formula();
    watchLists = null;
    compact(formula);

    Skeleton core = new Skeleton(numVariables);
    core.formula = formula;
    graph.serialize(formula);

    // logger.info("core = " + core.formula);

    return new Core(
        units.toArray(),
        java.util.Arrays.copyOfRange(proxies, numVariables, 2 * numVariables + 1),
        core);
  }

  /**
   * Simplifies the instance.
   *
   * @param isRoot true if solver is at the root of branching tree
   * @throws ContradictionException if contradiction was found
   */
  public void simplify(boolean isRoot) throws ContradictionException {
    propagate();

    if (isRoot) {
      renameEquivalentLiterals();
      graph.transitiveClosure();
      propagate();
    }

    for (int i = 0; i < Configure.numHyperBinaryResolutions; i++) {
      if (!HyperBinaryResolution.run(this)) {
        break;
      }
      propagate();
    }

    if (Configure.binarySelfSubsumming) {
      BinarySelfSubsumming.run(this);
      propagate();
    }

    if (Configure.subsumming) {
      Subsumming.run(this);
      propagate();
    }

    if (Configure.pureLiterals) {
      PureLiterals.run(this);
      propagate();
    }

    renameEquivalentLiterals();
    queueContradictions();
    propagateAll();

    propagateContradiction();
    MissingLiterals.run(this);
    propagateUnitsQueue();
    verifyIntegrity();
  }

  /**
   * Propagates all clauses in queue.
   *
   * @return true if any any clause was propagated.
   * @throws ContradictionException if a clause of length 0 was found
   */
  private boolean propagate() throws ContradictionException {
    boolean simplified = false;
    simplified = propagateShortClauses() || simplified;
    simplified = propagateUnitsQueue() || simplified;
    return simplified;
  }

  /** Runs propagation until fixed point */
  private boolean propagateAll() throws ContradictionException {
    boolean simplified = false;
    while (propagate()) {
      simplified = true;
    }
    return simplified;
  }

  public void propagateContradiction() throws ContradictionException {
    final TIntArrayList formula = watchLists.formula();
    final TIntArrayList clauses = watchLists.shortClauses();

    for (int i = 0; i < clauses.size(); i++) {
      int clause = clauses.get(i);
      if (!isClauseRemoved(formula, clause)) {
        int length = length(formula, clause);
        if (length == 0) {
          throw new ContradictionException();
        }
      }
    }
  }

  /** Propagates a list of short clauses */
  public boolean propagateShortClauses() throws ContradictionException {
    final TIntArrayList formula = watchLists.formula();
    final TIntArrayList clauses = watchLists.shortClauses();

    for (int i = 0; i < clauses.size(); i++) {
      int clause = clauses.get(i);
      if (isClauseRemoved(formula, clause)) {
        continue;
      }

      int length = length(formula, clause);
      int type = type(formula, clause);

      // logger.info("length = " + length);
      assert 0 <= length && length < 3;
      assert type == OR || type == XOR || type == NXOR;

      if (length == 0) {
        if (type != NXOR) {
          throw new ContradictionException();
        }
      } else if (length == 1) {
        if (type == NXOR) {
          queueUnit(-formula.get(clause));
        } else {
          queueUnit(formula.get(clause));
        }
      } else if (length == 2) {
        if (type == OR) {
          addBinary(formula.get(clause), formula.get(clause + 1));
        } else if (type == XOR) {
          addBinary(formula.get(clause), formula.get(clause + 1));
          addBinary(-formula.get(clause), -formula.get(clause + 1));
        } else {
          assert type == NXOR;
          addBinary(formula.get(clause), -formula.get(clause + 1));
          addBinary(-formula.get(clause), formula.get(clause + 1));
        }
      }

      watchLists.removeClause(clause);
      // watchLists.verifyIntegrity();
    }

    boolean simplified = !clauses.isEmpty();
    clauses.reset();
    return simplified;
  }

  /** Propagates all units */
  public boolean propagateUnitsQueue() throws ContradictionException {
    if (unitsQueue.isEmpty()) {
      return false;
    }

    // logger.info("before " + this);
    TIntArrayList propagated = graph.propagate(unitsQueue);
    // logger.info("propagating " + unitsQueue + " -> " + propagated);
    unitsQueue.reset();
    for (int i = 0; i < propagated.size(); i++) {
      int unit = propagated.get(i);
      assert !isLiteralAssigned(unit)
          : "Literal " + unit + " is already assigned";
      watchLists.assign(unit);
      units.add(unit);
    }
    // logger.info("after " + this);
    assert unitsQueue.isEmpty();
    return !propagated.isEmpty();
  }

  /** Finds equivalent literals and renames them */
  public void renameEquivalentLiterals() throws ContradictionException {
    // logger.info("units queue is " + unitsQueue);
    int[] collapsed = graph.removeStronglyConnectedComponents();
    for (int u = 1; u <= numVariables; ++u) {
      int proxy = collapsed[u];
      if (proxy != u) {
        renameLiteral(u, proxy);
      }
    }
    // logger.info("graph is " + graph);
    // logger.info("units queue is " + unitsQueue);
  }

  /** Queues contradictions in the implication graph */
  public void queueContradictions() {
    unitsQueue.add(graph.findContradictions().toNativeArray());
  }

  /** Returns number of binaries in implication graph containing u. */
  public int numBinaries(final int u) {
    return graph.edges(-u).size();
  }

  /** Renames from into to */
  private void renameLiteral(final int from, final int to) {
    // logger.info("Renaming " + from + " to " + to);
    watchLists.merge(from, to);
    watchLists.merge(-from, -to);

    proxies[from + numVariables] = to;
    proxies[-from + numVariables] = -to;
  }

  /** Checks solver for consistency.  */
  private void verifyIntegrity() {
    if (Configure.enableExpensiveChecks) {
      graph.verify();
      watchLists.verifyIntegrity();
      verifyAssignedLiterals();
    }
  }

  /** Verifies that assigned instances are removed.  */
  private void verifyAssignedLiterals() {
    for (int u = -numVariables; u <= numVariables; ++u) {
      if (u != 0 && isLiteralAssigned(u)) {
        assert watchLists.get(u).isEmpty()
            : "Assigned literal " + u + " has non empty watch list";
        assert graph.edges(u).isEmpty()
            : "Assigned literal " + u + " has neighbours in the implication graph";
      }
    }
  }

}
