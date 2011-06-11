package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.iterator.TIntIterator;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/** The core algorithms for sat solving. */
public final class Solver {
  private static final Logger logger = Logger.getLogger(Solver.class);

  /** Number of variables. */
  public int numVariables;
  /** Formula */
  public TIntArrayList formula;
  /** Set of true literals discovered. */
  public TouchSet units;
  /** Equalities between literals. */
  public int[] proxies;
  /** The implication graph. */
  public ImplicationsGraph graph;
  /** Watchlists. */
  public WatchLists watchLists;
  /** Units queue. */
  public TIntArrayList unitsQueue;

  /** Constructor. */
  public Solver(final Skeleton instance, final int branch)
      throws ContradictionException {
    numVariables = instance.numVariables;
    formula = new TIntArrayList(instance.formula);
    units = new TouchSet(numVariables);
    graph = new ImplicationsGraph(numVariables);
    watchLists = new WatchLists(numVariables, formula);
    unitsQueue = new TIntArrayList();

    proxies = new int[2 * numVariables + 1];
    for (int u = -numVariables; u <= numVariables; ++u) {
      proxies[u + numVariables] = u;
    }

    // Builds the watch lists.
    watchLists.build();
    if (branch != 0) {
      TIntArrayList temp = new TIntArrayList();
      temp.add(encode(1, OR));
      temp.add(branch);
      watchLists.append(temp);
    }

    if (Configure.verbose) {
      System.err.print(".");
      System.err.flush();
    }
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
    assert !isLiteralAssigned(u) && !isLiteralAssigned(v);

    if (u == v) {
      queueUnit(u);
    }

    graph.add(neg(u), v);
  }

  /**
   * Returns a literal to branch on.
   *
   * @return solution or simplified instance.
   */
  public Solution solve(boolean top) {
    try {
      if (top) {
        if (Configure.pureLiterals) {
          PureLiterals.run(this);
        }
        if (Configure.hyperBinaryResolution) {
          HyperBinaryResolution.run(this);
        }
      }

      simplify();

      // If all clauses were removed solve the remaining 2SAT.
      boolean empty = isEmptyFormula(formula);
      return empty ? solve2SAT() : Solution.unknown();
    } catch (ContradictionException e) {
      // logger.info("found contradiction", e);
      return Solution.unsatisfiable();
    }
  }

  /**
   * Solves the remaining 2SAT encoded in the implication graph
   */
  private Solution solve2SAT() throws ContradictionException {
    try {
      // Collapses strongly connected components and removes contradictions.
      // No new binary clause is created because there are no clauses
      // of longer length.
      renameEquivalentLiterals();

      TIntArrayList assigned = new TIntArrayList();
      for (int u = 1; u <= numVariables; ++u) {
        if (isLiteralAssigned(u)) {
          assigned.add(u);
        }
      }

      units.add(graph.solve(assigned));
    } catch (ContradictionException e) {
      return Solution.unsatisfiable();
    }

    // Satisfy literals with proxies.
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
        proxies[neg(literal) + numVariables] = neg(literal);
      }
    }

    return Solution.satisfiable(units.toArray());
  }

  /**
   * Returns core after simplifications.
   */
  public Core core() {
    assert unitsQueue.isEmpty();

    watchLists = null;
    compact(formula);
    return new Core(numVariables, units.toArray(), proxies, formula);
  }

  /**
   * Simplifies the instance.
   *
   * @throws ContradictionException if contradiction was found
   */
  public void simplify() throws ContradictionException {
    propagate();

    if (Configure.hyperBinaryResolution) {
      HyperBinaryResolution.run(this);
    }

    if (Configure.hiddenTautologyElimination) {
      HiddenTautologyElimination.run(this);
    }

    if (Configure.selfSubsumming) {
      SelfSubsumming.run(this);
    }

    renameEquivalentLiterals();

    if (Configure.pureLiterals) {
      PureLiterals.run(this);
    }

    MissingLiterals.run(this);
    verifyIntegrity();
  }

  /** Propagates units and binaries */
  public boolean propagate() throws ContradictionException {
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
    }

    clauses.reset();
    return simplified;
  }

  /** Propagates all discovered units. */
  public boolean propagateUnits() throws ContradictionException {
    // logger.info("from unitsQueue");
    boolean simplified = propagateLiterals(unitsQueue);
    unitsQueue.reset();

    TIntArrayList clauses = watchLists.units;
    TIntArrayList literals = new TIntArrayList(1);

    // TODO: all available units can be propagated simulatneously
    // logger.info("from watchLists");
    while (!clauses.isEmpty()) {
      literals.reset();
      for (int i = 0; i < clauses.size(); i++) {
        int clause = clauses.getQuick(i);
        if (!isClauseRemoved(formula, clause)) {
          int type = type(formula, clause);
          int unit = formula.get(clause);
          if (type == NXOR) {
            unit = neg(unit);
          }
          literals.add(unit);
        }
      }

      clauses.reset();
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

  public String toString() {
    return formulaToString(formula) + "\n" + graph.toString();
  }

  /** Finds equivalent literals and renames them */
  public void renameEquivalentLiterals() throws ContradictionException {
    int[] collapsed = graph.removeStronglyConnectedComponents();
    for (int u = 1; u <= numVariables; ++u) {
      if (collapsed[u] != u) {
        renameLiteral(u, collapsed[u]);
      }
    }
    propagate();
  }

  /** Returns number of binaries in implication graph containing u. */
  public int numBinaries(final int u) {
    return graph.edges(-u).size();
  }

  public int numClauses(final int u) {
    return watchLists.get(u).size() + numBinaries(u);
  }

  /** Renames from into to */
  private void renameLiteral(final int from, final int to)
      throws ContradictionException {
    // logger.info("Renaming " + from + " to " + to);
    watchLists.merge(from, to);
    watchLists.merge(neg(from), neg(to));

    proxies[from + numVariables] = to;
    proxies[neg(from) + numVariables] = neg(to);
  }

  /** Checks solver for consistency.  */
  public void verifyIntegrity() {
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
