package ibis.structure;

import java.text.DecimalFormat;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TLongArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntIterator;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import static ibis.structure.BitSet.mapZtoN;
import static ibis.structure.BitSet.mapNtoZ;;


/**
 * Solver.
 *
 * Represents the core SAT solver.
 *
 * TODO:
 * 1) Binary self summing
 * 2) Blocked clause elimination
 */
public final class Solver {
  private static final Logger logger = Logger.getLogger(Solver.class);
  private static final int REMOVED = Integer.MAX_VALUE;
  private static final int BACKTRACK_THRESHOLD = 0; // 1 << 8;
  private static final int BACKTRACK_MAX_CALLS = 1 << 12;
  private static final java.util.Random random = new java.util.Random(1);

  // Number of variables
  private int numVariables;
  // Set of true literals discovered.
  private BitSet units = new BitSet();
  // Equalities between literals.
  private int[] proxies;
  // The implication graph.
  private DAG dag;
  // List of clauses separated by 0.
  private TIntArrayList clauses;
  // Watchlists
  private TIntHashSet[] watchLists;
  // Maps start of clause to # unsatisfied literals
  private TIntIntHashMap lengths = new TIntIntHashMap();
  // Queue of clauses with at most 2 unsatisfied literals.
  // May contains satisfied clauses.
  private TIntArrayList queue = new TIntArrayList();

  public Solver(final Skeleton instance, final int branch) {
    numVariables = instance.numVariables;
    proxies = new int[numVariables + 1];
    dag = new DAG(numVariables);
    clauses = (TIntArrayList) instance.clauses.clone();
    watchLists = new TIntHashSet[2 * numVariables + 1];

    for (int literal = 1; literal <= numVariables; ++literal) {
      watchLists[mapZtoN(literal)] = new TIntHashSet();
      watchLists[mapZtoN(-literal)] = new TIntHashSet();
      proxies[literal] = literal;
    }

    buildWatchLists();
    if (branch != 0) {
      addUnit(branch);
    }

    // logger.info("Solving " + clauses.size() + " literals and "
                // + numVariables + " variables, branching on " + branch);
    System.err.print(".");
    System.err.flush();
  }

  /**
   * Builds watch lists.
   */
  private void buildWatchLists() {
    int start = 0;
    boolean satisfied = false;

    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.get(i);
      if (literal == 0) {
        int length = i - start;
        lengths.put(start, length);
        if (length <= 2) queue.add(start);
        if (satisfied) removeClause(start);
        start = i + 1;
        satisfied = false;
      } else {
        if (watchList(literal).contains(start)) {  // duplicate literal
          logger.error("Found duplicate literal");
          clauses.set(i, REMOVED);
        } else {
          watchList(literal).add(start);
          if (watchList(-literal).contains(start)) {
            logger.error("Found satisfied clause");
            satisfied = true;
          }
        }
      }
    }
  }

  /**
   * Adhoc function to remove REMOVED from clauses.
   */
  public TIntArrayList cleanClauses() {
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
   * @return a skeleton
   */
  public Skeleton skeleton() {
    Skeleton skeleton = new Skeleton();

    // Appends the implications graph and clauses
    skeleton.append(dag.skeleton());
    skeleton.append(cleanClauses());

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
   */
  public DAG dag() {
    return dag;
  }

  /**
   * Returns string representation of stored instance.
   */
  public String toString() {
    Skeleton skeleton = skeleton();
    skeleton.canonicalize();
    return skeleton.toString();
  }

  /**
   * Returns a normalized literal to branch on.
   */
  public Solution solve() {
    int beforeNumLiterals = clauses.size();

    int solved = Solution.UNKNOWN;
    try {
      simplify();
    } catch (ContradictionException e) {
      return Solution.unsatisfiable();
    }

    // Computes proxies for every literal.
    for (int literal = 1; literal <= numVariables; ++literal) {
      getRecursiveProxy(literal);
    }

    if (clauses.size() == 0) {
      // Solves the remaining 2SAT encoded in the implication graph
      for (TIntIterator it = dag.solve().iterator(); it.hasNext(); ){
        addUnit(it.next());
      }
      return Solution.satisfiable(units.elements(), proxies);
    }

    // Gets the core instance that needs to be solved further
    Skeleton core = new Skeleton();
    core.append(dag.skeleton());
    core.append(cleanClauses());

    // The idea here is that if a literal is frequent
    // it will have a higher chance to be selected
    int bestBranch = 0;
    int bestValue = Integer.MIN_VALUE;
    for (int i = 0; i < core.clauses.size(); ++i) {
      int literal = core.clauses.get(i);
      if (literal != 0) {
        int value = random.nextInt();
        if (value > bestValue) {
          bestValue = value;
          bestBranch = literal;
        }
      }
    }

    int afterNumLiterals = core.clauses.size();
    // logger.info("Reduced from " + beforeNumLiterals + " to " + afterNumLiterals
                // + " (diff " + (beforeNumLiterals - afterNumLiterals) + ")");
    return Solution.unknown(units.elements(), proxies, core, bestBranch);
  }

  /**
   * Simplifies the instance.
   */
  public void simplify() throws ContradictionException {
    propagate();
    for (int i = 0; i < Configure.numHyperBinaryResolutions; ++i) {
      if (!hyperBinaryResolution()) break;
      propagate();
    }

    if (Configure.pureLiterals()) {
      pureLiterals();
      propagate();
    }
  }

  /**
   * Propagates all clauses in queue.
   */
  private boolean propagate() throws ContradictionException {
    // NOTE: any new clause is appended.
    for (int i = 0; i < queue.size(); ++i) {
      int start = queue.get(i);
      if (isSatisfied(start)) {
        continue;
      }
      int length = lengths.get(start);
      switch (length) {
        case 0: {
          throw new ContradictionException();
        }

        case 1: {  // unit
          int u;
          while ((u = clauses.get(start)) == REMOVED) {
            start++;
          }
          addUnit(u);
          assert isSatisfied(start);
          break;
        }

        case 2: {  // binary
          int u, v;
          while ((u = clauses.get(start)) == REMOVED) {
            start++;
          }
          start++;
          while ((v = clauses.get(start)) == REMOVED) {
            start++;
          }
          addBinary(u, v);

          if (!isSatisfied(start)) {
            removeClause(start);
          }
          break;
        }
      }
    }

    boolean simplified = !queue.isEmpty();
    queue.clear();
    return simplified;
  }

  /**
   * Hyper-binary resolution.
   *
   * If (a1 + ... ak + b) and (l &ge; -a1) ... (l &ge; -ak)
   * then l &ge; b, otherwise if l then clause is contradiction
   *
   * @return true if any unit or binary was discovered
   */
  public boolean hyperBinaryResolution() throws ContradictionException {
    // logger.debug("Running hyperBinaryResolution()");
    int[] counts = new int[2 * numVariables + 1];
    int[] sums = new int[2 * numVariables + 1];
    int[] touched = new int[2 * numVariables + 1];
    int numUnits = 0, numBinaries = 0;

    for (int start = 0; start < clauses.size(); start++) {
      int numLiterals = 0;
      int clauseSum = 0;
      int numTouched = 0;

      for (; start < clauses.size(); start++) {
        int literal = clauses.get(start++);
        if (literal == 0) break;
        if (literal == REMOVED) continue;

        numLiterals++;
        clauseSum += literal;
        TIntHashSet neighbours = dag.neighbours(literal);
        if (neighbours != null) {
          TIntIterator it = neighbours.iterator();
          for (int size = neighbours.size(); size > 0; --size) {
            int node = mapZtoN(-it.next());
            if (counts[node] == 0) touched[numTouched++] = node;
            counts[node] += 1;
            sums[node] += literal;
          }
        }
      }

      if (numLiterals < 3) {
        // Clause is too small for hyper binary resolution.
        continue;
      }

      for (int i = 0; i < numTouched; ++i) {
        int touch = touched[i];
        int literal = mapNtoZ(touch);
        literal = getRecursiveProxy(literal);

        if (counts[touch] == numLiterals) {
          // There is an edge from literal to all literals in clause.
          if (!units.contains(-literal)) {
            addUnit(-literal);
            ++numUnits;
          }
        } else if (counts[touch] + 1 == numLiterals) {
          // There is an edge from literal to all literals in clause except one.
          if (isAssigned(literal)) continue;
          int missing = clauseSum - sums[touch];
          if (!dag.containsEdge(literal, missing)) {
            addBinary(-literal, missing);
            ++numBinaries;
          }
        }

        counts[touch] = 0;
        sums[touch] = 0;
      }
    }

    // logger.debug("Hyper binary resolution found " + numUnits + " unit(s) and "
    //              + numBinaries + " binary(ies)");
    return numUnits > 0 || numBinaries > 0;
  }

  /**
   * Pure literal assignment.
   *
   * @return true if any unit was discovered
   */
  public boolean pureLiterals() throws ContradictionException {
    int numUnits = 0;

    for (int u = 1; u <= numVariables; u++) {
      if (isAssigned(u)) {
        continue;
      }
      if (numBinaries(u) == 0 && watchList(u).size() == 0) {
        addUnit(-u);
        numUnits++;
        continue;
      }
      if (numBinaries(-u) == 0 && watchList(-u).size() == 0) {
        addUnit(u);
        numUnits++;
        continue;
      }
    }

    return numUnits > 0;
  }

  /**
   * Returns the watch list for literal u.
   */
  private TIntHashSet watchList(int u) {
    return watchLists[mapZtoN(u)];
  }

  /**
   * Returns true if u is already assigned.
   */
  private boolean isAssigned(final int u) {
    assert u != 0;
    return getRecursiveProxy(u) != u || units.get(u) || units.get(-u);
  }

  /**
   * Returns true if clause was satisfied.
   */
  private boolean isSatisfied(final int clause) {
    return !lengths.contains(clause);
  }

  /**
   * Returns number of binaries (in DAG) containing literal.
   */
  private int numBinaries(int literal) {
    TIntHashSet neighbours = dag.neighbours(-literal);
    return neighbours == null ? 0 : neighbours.size() - 1;
  }

  /**
   * Given the start of a clause return position of literal u.
   */
  private int findLiteral(final int clause, final int u) {
    for (int c = clause; ; c++) {
      if (u == clauses.get(c)) {
        return c;
      }
    }
  }

  /**
   * Removes literal u from clause.
   *
   * @param clause start of clause
   * @param u literal to remove
   */
  private void removeLiteral(final int clause, final int u) {
    assert u != 0;
    watchList(u).remove(clause);
    clauses.set(findLiteral(clause, u), REMOVED);
    assert lengths.contains(clause);
    int newLength = lengths.adjustOrPutValue(clause, -1, 0xA3A3A3A3);
    if (newLength <= 2) queue.add(clause);
  }

  /**
   * Removes one clause updating the watchlists.
   *
   * @param clause start of clause
   */
  private void removeClause(final int clause) {
    lengths.remove(clause);
    for (int c = clause; ; c++) {
      int literal = clauses.get(c);
      clauses.set(c, REMOVED);
      if (literal == 0) break;
      if (literal == REMOVED) continue;
      watchList(literal).remove(clause);
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
   * Adds a new unit.
   *
   * @param u unit to add.
   */
  private void addUnit(final int u) {
    assert !isAssigned(u);

    if (dag.hasNode(u)) {
      int[] neighbours = dag.neighbours(u).toArray();
      for (int unit : neighbours) {
        propagateUnit(u);
        units.add(u);
      }
      dag.delete(neighbours);
    } else {
      propagateUnit(u);
      units.add(u);
    }
  }

  /**
   * Adds a new binary unit.
   */
  private void addBinary(final int u, final int v) {
    assert !isAssigned(u);
    assert !isAssigned(v);

    // propagates contradictions contradictions
    TIntHashSet contradictions = dag.findContradictions(-u, v);
    if (!contradictions.isEmpty()) {
      int[] contradictions_ = contradictions.toArray();
      for (int unit : contradictions_) {
        addUnit(unit);
      }
      dag.delete(contradictions_);
      if (contradictions.contains(u) || contradictions.contains(v)) {
        return;
      }
    }

    DAG.Join join = dag.addEdge(-u, v);
    if (join != null) {
      TIntIterator it;
      for (it = join.children.iterator(); it.hasNext();) {
        renameLiteral(it.next(), join.parent);
      }
    }
  }

  /**
   * Merges watchlist of u into v
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
        int position = findLiteral(clause, u);
        clauses.set(position, v);
        watchList(v).add(clause);
      }
    }
  }

  /**
   * Renames literal u to v.
   *
   * After call:
   *   - always: getRecursiveProxy(u) == getRecursiveProxy(v)
   *   - immediate: proxies[u] == v
   *
   * @param u old name
   * @param v new name
   */
  private void renameLiteral(final int u, final int v) {
    assert !isAssigned(u);
    assert !isAssigned(v);

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
   */
  private int getRecursiveProxy(final int u) {
    assert u != 0;
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
