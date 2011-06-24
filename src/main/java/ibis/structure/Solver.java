package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/** The core algorithms for sat solving. */
public final class Solver {
  private static final Logger logger = Logger.getLogger(Solver.class);

  /** Number of variables. */
  public int numVariables;
  /** Formula */
  public TIntArrayList formula;
  /** Literal to branch on. */
  public int branched;
  /** Set of true literals discovered. */
  public TIntHashSet units;
  /** Equalities between literals. */
  public int[] proxies;
  /** The implication graph. */
  public ImplicationsGraph graph;
  /** Watchlists. */
  public WatchLists watchLists;
  /** Units queue. */
  public TIntArrayList unitsQueue;

  /** Constructor. */
  public Solver(final Skeleton instance)
      throws ContradictionException {
    instance.expandSmallXOR();

    numVariables = instance.numVariables;
    formula = instance.formula;
    units = new TIntHashSet();
    graph = new ImplicationsGraph(numVariables);
    watchLists = new WatchLists(numVariables, formula);
    unitsQueue = new TIntArrayList();

    proxies = new int[2 * numVariables + 1];
    for (int u = 1; u <= numVariables; u++) {
      proxies[u + numVariables] = u;
      proxies[neg(u) + numVariables] = neg(u);
    }

    watchLists.build();

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
        && !units.contains(u) && !units.contains(neg(u))
        && watchLists.get(u).isEmpty() && watchLists.get(neg(u)).isEmpty();
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
    } else {
      assert -numVariables <= u && u <= numVariables;
      assert -numVariables <= v && v <= numVariables;
      graph.add(neg(u), v);
    }
  }

  public Solution solve() throws ContradictionException {
    assert unitsQueue.isEmpty();
    verifyIntegrity();

    // If all clauses were removed solve the remaining 2SAT.
    boolean _2SAT = true;
    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext() && _2SAT) {
      int clause = it.next();
      int length = length(formula, clause);
      _2SAT = length <= 2;
    }

    return _2SAT ? solve2SAT() : Solution.unknown();
  }

  /**
   * Solves the remaining 2SAT encoded in the implication graph
   */
  private Solution solve2SAT() throws ContradictionException {
    // Makes sure that all binaries (including XORs) are in the graph.
    propagateBinaries();

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

    units.addAll(graph.solve(assigned));

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

    return Solution.satisfiable(units);
  }

  /**
   * Returns core after simplifications.
   */
  public Core core() {
    verifyIntegrity();
    watchLists = null;
    compact(formula);

    TIntArrayList tmp = new TIntArrayList();
    for (int u = 1; u <= numVariables; u++) {
      int v = proxy(u);
      if (u != v) {
        tmp.add(u);
        tmp.add(v);
      }
    }

    return new Core(numVariables, new TIntArrayList(units), tmp, formula);
  }

  /** Propagates units and binaries */
  public boolean propagate() throws ContradictionException {
    boolean simplified = false;
    
    simplified = propagateUnits() || simplified;
    simplified = propagateBinaries() || simplified;
    return simplified;
  }

  /**
   * Putes discovered binaries in the graph.
   * Formula is not modified.
   */
  public boolean propagateBinaries() {
    TIntArrayList clauses = watchLists.binaries;
    boolean simplified = false;

    for (int i = 0; i < clauses.size(); i++) {
      int clause = clauses.getQuick(i);
      if (isClauseRemoved(formula, clause)) {
        continue;
      }

      int length = length(formula, clause);
      int type = type(formula, clause);
      if (length == 1) {
        queueUnit(formula.getQuick(clause));
        continue;
      }

      simplified = true;
      int l0 = formula.getQuick(clause);
      int l1 = formula.getQuick(clause + 1);

      if (type == OR) {
        addBinary(l0, l1);
      } else if (type == XOR) {
        addBinary(l0, l1);
        addBinary(neg(l0), neg(l1));
      } else {
        assert type == NXOR;
        addBinary(l0, neg(l1));
        addBinary(neg(l0), l1);
      }
    }

    clauses.reset();
    return simplified;
  }

  /** Propagates all discovered units. */
  public boolean propagateUnits() throws ContradictionException {
    boolean simplified = propagateLiterals(unitsQueue);
    unitsQueue.reset();

    TIntArrayList clauses = watchLists.units;
    TIntArrayList literals = new TIntArrayList(1);

    while (!clauses.isEmpty()) {
      literals.reset();
      for (int i = 0; i < clauses.size(); i++) {
        int clause = clauses.getQuick(i);
        if (!isClauseRemoved(formula, clause)) {
          int unit = formula.get(clause);
          if (type(formula, clause) == NXOR) {
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
    for (int i = 0; i < propagated.size(); i++) {
      int unit = propagated.getQuick(i);
      unit = proxy(unit);

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
    return graph.edges(neg(u)).size();
  }

  public int numClauses(final int u) {
    return watchLists.get(u).size();
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
        /*
        assert graph.edges(u).isEmpty()
            : "Assigned literal " + u + " has neighbours in the implication graph";
            */
      }
    }
  }
}
