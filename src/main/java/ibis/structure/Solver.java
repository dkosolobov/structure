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
    // logger.info("instance\n" + instance);
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

  /** Queues a new assigned unit. */
  public void queueUnit(final int u) {
    unitsQueue.add(u);
  }

  /** Adds implication -u &rarr; v. */
  public void addBinary(final int u, final int v) {
    // logger.info("u = " + u + " v = " + v);
    // logger.info("u = " + units.contains(u) + " v = " + units.contains(v));
    // logger.info("u = " + units.contains(-u) + " v = " + units.contains(-v));
    // logger.info("u = " + proxy(u) + " v = " + proxy(v));
    assert !isLiteralAssigned(u) && !isLiteralAssigned(v);
    assert u != v;
    graph.add(-u, v);
  }

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
      // logger.info("found contradiction", e);
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
      // graph.transitiveClosure();
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
      (new Subsumming(this)).run();
      propagate();
    }

    if (Configure.pureLiterals) {
      PureLiterals.run(this);
      propagate();
    }

    renameEquivalentLiterals();
    queueContradictions();
    propagate();

    for (int u = 1; u <= numVariables; ++u) {
      if (watchLists.get(u).size() == 1) {
        if (type(watchLists.formula(), watchLists.get(u).toArray()[0]) != OR) {
          logger.info("dependent " + u);
        }
      }
    }

    MissingLiterals.run(this);
    propagateUnits();
    verifyIntegrity();
  }

  /** Propagates units and binaries */
  public boolean propagate() throws ContradictionException {
    TIntArrayList formula = watchLists.formula();
    TIntArrayList clauses = watchLists.binaries;
    boolean simplified = propagateUnits() || !clauses.isEmpty();

    for (int i = 0; i < clauses.size(); i++) {
      int clause = clauses.getQuick(i);
      if (isClauseRemoved(formula, clause)) {
        continue;
      }

      int length = length(formula, clause);
      int type = type(formula, clause);
      assert length == 2: "Length should be 2 not " + length;

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

      watchLists.removeClause(clause);
    }

    clauses.reset();
    return simplified;
  }

  /** Propagates all discovered units. */
  private boolean propagateUnits() throws ContradictionException {
    // logger.info("from unitsQueue");
    boolean simplified = propagateLiterals(unitsQueue);
    unitsQueue.reset();

    TIntArrayList clauses = watchLists.units;
    TIntArrayList literals = new TIntArrayList(1);

    // TODO: all available units can be propagated simulatneously
    // logger.info("from watchLists");
    while (!clauses.isEmpty()) {
      if (watchLists.contradiction) {
        throw new ContradictionException();
      }

      int clause = clauses.get(clauses.size() - 1);
      clauses.remove(clauses.size() - 1, 1);
      if (isClauseRemoved(watchLists.formula(), clause)) {
        continue;
      }

      int type = type(watchLists.formula(), clause);
      int unit = watchLists.formula.get(clause);
      if (type == NXOR) {
        unit = neg(unit);
      }

      literals.reset();
      literals.add(unit);
      simplified = propagateLiterals(literals) || simplified;
    }

    return simplified;
  }

  /** Propagates all units */
  private boolean propagateLiterals(final TIntArrayList literals) 
      throws ContradictionException {
    boolean simplified = false;
    TIntArrayList propagated = graph.propagate(literals);
    // logger.info("propagating " + literals + " -> " + propagated);
    for (int i = 0; i < propagated.size(); i++) {
      int unit = propagated.getQuick(i);
      assert proxy(unit) == unit;

      if (units.contains(unit)) {
        continue;
      }
      if (units.contains(neg(unit))) {
        throw new ContradictionException();
      }

      watchLists.assign(unit);
      units.add(unit);
      simplified = true;
    }

    return simplified;
  }

  /** Finds equivalent literals and renames them */
  public void renameEquivalentLiterals() throws ContradictionException {
    int[] collapsed = graph.removeStronglyConnectedComponents();
    for (int u = 1; u <= numVariables; ++u) {
      if (collapsed[u] != u) {
        renameLiteral(u, collapsed[u]);
      }
    }
  }

  /** Queues contradictions in the implication graph */
  @Deprecated
  public void queueContradictions() {
    graph.findContradictions(unitsQueue);
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
