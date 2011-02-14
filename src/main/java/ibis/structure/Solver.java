package ibis.structure;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntLongHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntIterator;
import org.apache.log4j.Logger;

import static ibis.structure.BitSet.mapZtoN;
import static ibis.structure.BitSet.mapNtoZ;;


/**
 * The core algorithms for sat solving.
 *
 * TODO:
 * 1) Blocked clause elimination
 */
public final class Solver {
  /** Marks a removed literal */
  public static final int REMOVED = Integer.MAX_VALUE;
  private static final TIntHashSet EMPTY = new TIntHashSet();
  private static final Logger logger = Logger.getLogger(Solver.class);

  /** Number of variables. */
  public int numVariables;
  /** Set of true literals discovered. */
  public BitSet units = new BitSet();
  /** Equalities between literals. */
  private int[] proxies;
  /** The implication graph. */
  public ImplicationsGraph graph;
  /** List of clauses separated by 0. */
  public TIntArrayList clauses;
  /** Watchlists. */
  private TIntHashSet[] watchLists;
  /** Maps start of clause to # unsatisfied literals. */
  public TIntIntHashMap lengths = new TIntIntHashMap();
  /** Queue of clauses with at most 2 unsatisfied literals. */
  private TIntArrayList queue = new TIntArrayList();
  /** Queue of units to be propagated. */
  private TIntArrayList unitsQueue = new TIntArrayList();


  /**
   * Constructor.
   *
   * @param instance instance to solve
   */
  public Solver(final Skeleton instance) {
    numVariables = instance.numVariables;
    proxies = new int[numVariables + 1];
    graph = new ImplicationsGraph(numVariables);
    clauses = (TIntArrayList) instance.clauses.clone();

    for (int u = 1; u <= numVariables; ++u) {
      proxies[u] = u;
    }

    if (Configure.verbose) {
      System.err.print(".");
      System.err.flush();
    }
  }

  /**
   * Builds watch lists.
   *
   * Watch list for a literal u contains the start position of clauses containing u.
   */
  private void buildWatchLists() throws ContradictionException {
    watchLists = new TIntHashSet[2 * numVariables + 1];
    for (int u = -numVariables; u <= numVariables; ++u) {
      watchLists[u + numVariables] = new TIntHashSet();
    }

    for (int start = 0; start < clauses.size();) {
      if (clauses.get(start + 0) == 0) {  // empty clause
        throw new ContradictionException();
      }

      if (clauses.get(start + 1) == 0) {  // unit
        unitsQueue.add(clauses.getSet(start, REMOVED));
        clauses.set(start + 1, REMOVED);
        start += 2;
        continue;
      }

      if (clauses.get(start + 2) == 0) {  // binary
        addBinary(clauses.getSet(start, REMOVED),
                  clauses.getSet(start + 1, REMOVED));
        clauses.set(start + 2, REMOVED);
        start += 3;
        continue;
      }

      for (int end = start; ; end++) {
        int u = clauses.get(end);
        if (u == 0) {
          assert end - start >= 3;
          lengths.put(start, end - start);
          start = end + 1;
          break;
        }

        assert !watchList(u).contains(start): "Found duplicate literal";
        assert !watchList(-u).contains(start): "Found satisfied clause";
        watchList(u).add(start);
      }
    }
  }

  /**
   * Returns the watch list for a literal u.
   *
   * @param u a literal
   * @return watch list of u
   */
  public TIntHashSet watchList(final int u) {
    return watchLists[u + numVariables];
  }

  /** Queues a new assigned unit. */
  public void queueUnit(int unit) {
    unitsQueue.add(unit);
  }

  /**
   * Returns a string representation of clause
   * starting at start.
   *
   * @param start position of first literal in clause
   * @return a string representing the clause
   */
  private String clauseToString(final int start) {
    StringBuffer string = new StringBuffer();
    string.append("{");
    for (int i = start;; i++) {
      int literal = clauses.get(i);
      if (literal == 0) {
        break;
      }
      if (literal == REMOVED) {
        continue;
      }
      string.append(literal);
      string.append(", ");
    }
    string.append("}");
    return string.toString();
  }

  /**
   * Verifies consistency of lengths array.
   */
  private void verifyLengths() {
    TIntIntIterator it = lengths.iterator();
    for (int size = lengths.size(); size > 0; size--) {
      it.advance();
      int start = it.key();
      int length = 0;

      for (int end = start;; end++) {
        int literal = clauses.get(end);
        if (literal == 0) {
          break;
        }
        if (literal == REMOVED) {
          continue;
        }

        length++;
        assert !isLiteralAssigned(literal)
            : "Literal " + literal + " is assigned but clause not satisfied";
        assert watchList(literal).contains(start)
            : "Clause " + start + " not in watch list of " + literal;
      }
      assert it.value() == length
          : "Clause " + start + " has wrong length";
    }
  }

  /** Verifies watch lists. */
  private void verifyWatchLists() {
    for (int u = -numVariables; u <= numVariables; ++u) {
      if (u != 0) {
        TIntIterator it = watchList(u).iterator();
        for (int size = watchList(u).size(); size > 0; size--) {
          int start = it.next();
          assert lengths.containsKey(start)
              : "Watch list of " + u + " contains satisfied clause";
          findLiteral(start, u);  // NOTE: uses findLiteral's internal check
        }
      }
    }
  }

  /** Verifies that assigned instances are removed.  */
  private void verifyAssigned() {
    for (int u = -numVariables; u <= numVariables; ++u) {
      if (u != 0 && isLiteralAssigned(u)) {
        assert watchList(u).isEmpty()
            : "Assigned literal " + u + " has non empty watch list";
        // assert dag.neighbours(u) == null
            // : "Assigned literal " + u + " is in dag";
      }
    }
  }

  /** Checks solver for consistency.  */
  private void verify() {
    if (Configure.enableExpensiveChecks) {
      graph.verify();
      verifyLengths();
      verifyWatchLists();
      verifyAssigned();
    }
  }

  /**
   * Adhoc function to remove REMOVED from clauses.
   *
   * @return clauses with REMOVED elements removed
   */
  public TIntArrayList compact() {
    TIntArrayList clean = new TIntArrayList();
    for (int i = 0; i < clauses.size(); i++) {
      int literal = clauses.get(i);
      if (literal != REMOVED) {
        clean.add(literal);
      }
    }
    return clean;
  }

  /**
   * Returns current (simplified) instance.
   *
   * Includes units and equivalent literals.
   *
   * @return a skeleton with the instance.
   */
  public Skeleton skeleton() {
    Skeleton skeleton = new Skeleton();

    // Appends the implications graph and clauses
    skeleton.append(graph.skeleton());
    skeleton.append(compact());

    // Appends units and equivalent relations
    for (int literal = -numVariables; literal <= numVariables; ++literal) {
      if (literal != 0) {
        int proxy = getRecursiveProxy(literal);
        if (units.contains(proxy)) {
          skeleton.add(literal);
        } else if (literal != proxy && !units.contains(-proxy)) {
          // literal and proxy are equivalent,
          // but proxy is not assigned
          skeleton.add(literal, -proxy);
        }
      }
    }

    return skeleton;
  }

  /**
   * Returns the current DAG.
   *
   * @return current stored directed acyclic graph of literals.
   */
  public ImplicationsGraph graph() {
    return graph;
  }

  /**
   * Returns string representation of stored instance.
   *
   * @return a string representing the instance.
   */
  public String toString() {
    return skeleton().toString();
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
        unitsQueue.add(branch);
      }
      buildWatchLists();
      simplify(branch == 0);
    } catch (ContradictionException e) {
      return Solution.unsatisfiable();
    }

    // Checks if all clauses were satisfied.
    boolean satisfied = true;
    for (int u = -numVariables; u <= numVariables; u++) {
      if (u != 0) {
        if (!watchList(u).isEmpty()) {
          satisfied = false;
          break;
        }
      }
    }

    if (satisfied) {
      // Solves the remaining 2SAT encoded in the implication graph
      try {
        // Collapses strongly connected components and removes contradictions.
        // No new binary clause is created because there are no clauses
        // of longer length.
        simplifyImplicationGraph();
        propagate();

        TIntArrayList assigned = new TIntArrayList(units.elements());
        for (int u = 1; u <= numVariables; ++u) {
          if (proxies[u] != u) {
            assigned.add(u);
          }
        }

        TIntArrayList newUnits = graph.solve(assigned);
        units.addAll(newUnits.toNativeArray());
      } catch (ContradictionException e) {
        return Solution.unsatisfiable();
      }
    }

    // Satisfies literals with satisfied proxies.
    for (int literal = 1; literal <= numVariables; ++literal) {
      int proxy = getRecursiveProxy(literal);
      if (literal != proxy) {
        if (units.contains(proxy)) {
          units.add(literal);
          proxies[literal] = literal;
        } else if (units.contains(-proxy)) {
          units.add(-literal);
          proxies[literal] = literal;
        }
      }
    }

    if (satisfied) {
      return Solution.satisfiable(units.elements());
    }

    return Solution.unknown();
  }

  public Core core() {
    Skeleton core = new Skeleton();
    core.numVariables = numVariables;
    core.append(graph.skeleton());
    // logger.info("core size is " + core.clauses.size());
    // System.exit(1);
    core.append(compact());
    return new Core(units.elements(), proxies, core);
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
      if (Configure.pureLiterals) {
        PureLiterals.run(this);
        propagate();
      }
    }

    renameEquivalentLiterals();
    graph.transitiveClosure();
    propagate();

    for (int i = 0; i < Configure.numHyperBinaryResolutions; ++i) {
      if (!HyperBinaryResolution.run(this)) {
        break;
      }
      propagate();

      renameEquivalentLiterals();
      unitsQueue.add(graph.findContradictions().toNativeArray());
      propagate();
    }

    renameEquivalentLiterals();
    unitsQueue.add(graph.findAllContradictions().toNativeArray());
    propagate();

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
    MissingLiterals.run(this);
    verify();
  }

  /**
   * Propagates one clause.
   *
   * @param start position of first literal in the clause to be propagated
   * @throws ContradictionException if clause has length 0
   */
  private void propagateClause(final int start) throws ContradictionException {
    if (isClauseSatisfied(start)) {
      return;
    }
    int length = lengths.get(start);
    assert 0 <= length && length < 3
        : "Invalid clause of length " + length + " in queue";

    int position = start;
    int u, v;
    switch (length) {
      case 0:
        throw new ContradictionException();

      case 1:  // unit
        while ((u = clauses.get(position)) == REMOVED) {
          position++;
        }
        addUnit(u);
        assert isClauseSatisfied(start);
        break;

      case 2:  // binary
        while ((u = clauses.get(position)) == REMOVED) {
          position++;
        }
        position++;
        while ((v = clauses.get(position)) == REMOVED) {
          position++;
        }
        addBinary(u, v);
        removeClause(start);
        break;

      default:
        assert false : "Clause must have 0, 1 or 2 literals, not " + length;
    }
  }

  /**
   * Propagates one unit assignment.
   *
   * @param u unit to propagate
   */
  private void propagateUnit(final int u) {
    // Removes clauses satisfied by u.
    for (int clause : watchList(u).toArray()) {
      removeClause(clause);
    }
    assert watchList(u).isEmpty();

    // Removes -u.
    for (int clause : watchList(-u).toArray()) {
      removeLiteral(clause, -u);
    }
    assert watchList(-u).isEmpty();
  }

  /**
   * Propagates all clauses in queue.
   *
   * @return true if any any clause was propagated.
   * @throws ContradictionException if a clause of length 0 was found
   */
  private boolean propagate() throws ContradictionException {
    propagateUnitsQueue();

    // NOTE: any new clause is appended.
    for (int i = 0; i < queue.size(); ++i) {
      propagateClause(queue.get(i));
    }

    boolean simplified = !queue.isEmpty();
    queue.clear();
    return simplified;
  }

  public void renameEquivalentLiterals() throws ContradictionException {
    int[] collapsed = graph.removeStronglyConnectedComponents();
    for (int u = 1; u <= numVariables; ++u) {
      int proxy = collapsed[u];
      if (proxy != u) {
        assert proxies[u] == u : "Variable already renamed";
        assert proxy != 0 : "Colapsed to 0";
        renameLiteral(u, proxy);
      }
    }
  }

  public void simplifyImplicationGraph() throws ContradictionException {
    renameEquivalentLiterals();

    TIntArrayList propagated;
    if (graph.density() < Configure.ttc) {
      graph.transitiveClosure();
      propagated = graph.findContradictions();
    } else {
      // propagated = graph.findContradictions();
      propagated = graph.findAllContradictions();
    }

    unitsQueue.add(propagated.toNativeArray());
  }


  /** Returns true if literal u is already assigned. */
  public boolean isLiteralAssigned(final int u) {
    assert u != 0;
    return getRecursiveProxy(u) != u || units.get(u) || units.get(-u);
  }

  /**
   * Returns true if clause was satisfied.
   * NOTE: satisfied clauses are removed from lengths.
   */
  public boolean isClauseSatisfied(final int clause) {
    return !lengths.contains(clause);
  }

  /** Returns number of binaries (in the implication graph ) containing literal u. */
  public int numBinaries(final int u) {
    return graph.edges(-u).size();
  }

  /**
   * Given the start of a clause return position of literal u.
   *
   * @param clause position of first literal in clause
   * @param u literal in clause to be found
   * @return position of u (always &ge; clause)
   */
  private int findLiteral(final int clause, final int u) {
    for (int c = clause;; c++) {
      int literal = clauses.get(c);
      assert u == 0 || literal != 0
          : "Literal " + u + " is missing from clause";
      if (u == literal) {
        return c;
      }
    }
  }

  /** Removes literal u from clause. */
  public void removeLiteral(final int clause, final int u, final int position) {
    assert u != 0 : "Cannot remove literal 0 from clause";
    assert lengths.contains(clause);

    watchList(u).remove(clause);
    clauses.set(position, REMOVED);
    if (lengths.adjustOrPutValue(clause, -1, 0) <= 2) {
      queue.add(clause);
    }
  }

  /** Removes literal u from clause. */
  private void removeLiteral(final int clause, final int u) {
    removeLiteral(clause, u, findLiteral(clause, u));
  }

  /**
   * Removes one clause updating the watchlists.
   *
   * @param clause start of clause
   */
  public void removeClause(final int clause) {
    lengths.remove(clause);
    assert isClauseSatisfied(clause);
    for (int c = clause;; c++) {
      int literal = clauses.getSet(c, REMOVED);
      if (literal == REMOVED) {
        continue;
      }
      if (literal == 0) {
        break;
      }
      watchList(literal).remove(clause);
    }
  }


  /**
   * Propagates all units in unit queue.
   *
   * Usually this is faster than calling addUnit for each unit.
   */
  private void propagateUnitsQueue() throws ContradictionException {
    TIntArrayList propagated = graph.propagate(unitsQueue);
    unitsQueue.reset();

    for (int i = 0; i < propagated.size(); i++) {
      int v = propagated.get(i);
      propagateUnit(v);
      units.add(v);
    }
  }

  /**
   * Adds a new unit.
   *
   * @param u unit to add.
   */
  private void addUnit(final int u) throws ContradictionException {
    assert !isLiteralAssigned(u) : "Unit " + u + " already assigned";

    TIntArrayList propagated = graph.propagate(u);
    for (int i = 0; i < propagated.size(); i++) {
      int v = propagated.get(i);
      propagateUnit(v);
      units.add(v);
    }
  }

  /** Adds implication -u &rarr; v.  */
  public void addBinary(final int u, final int v) {
    assert !isLiteralAssigned(u) : "First literal " + u + " is assigned";
    assert !isLiteralAssigned(v) : "Second literal " + v + " is assigned";
    graph.add(-u, v);
  }

  /**
   * Merges watchlist of u into v.
   *
   * @param u source literal
   * @param v destination literal
   */
  private void mergeWatchLists(final int u, final int v) {
    for (int clause : watchList(u).toArray()) {
      if (watchList(v).contains(clause)) {
        // renaming creates duplicate
        removeLiteral(clause, u);
      } else if (watchList(-v).contains(clause)) {
        // renaming creates a tautology
        removeClause(clause);
      } else {
        // renames literal in clause
        int position = findLiteral(clause, u);
        clauses.set(position, v);
        watchList(v).add(clause);
      }
    }
    watchLists[u + numVariables] = EMPTY;
  }

  /**
   * Renames literal u to v.
   *
   * After call:
   *   - always: getRecursiveProxy(u) == getRecursiveProxy(v)
   *   - immediate: proxies[u] == v
   *
   * @param u old literal name
   * @param v new literal name
   */
  private void renameLiteral(final int u, final int v) {
    assert !isLiteralAssigned(u);
    assert !isLiteralAssigned(v);

    mergeWatchLists(u, v);
    mergeWatchLists(-u, -v);

    if (u < 0) {
      proxies[-u] = -v;
    } else {
      proxies[u] = v;
    }
  }

  /**
   * Recursively finds the proxy of u.
   * The returned value doesn't have any proxy.
   *
   * @param u literal
   * @return real name of u
   */
  private int getRecursiveProxy(final int u) {
    assert u != 0: "0 is not a valid literal";
    if (u < 0) {
      return -getRecursiveProxy(proxies[-u]);
    }
    if (u != proxies[u]) {
      proxies[u] = getRecursiveProxy(proxies[u]);
      return proxies[u];
    }
    return u;
  }
}
