package ibis.structure;

import java.text.DecimalFormat;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TLongArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntLongHashMap;
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

  public Solver(final Skeleton instance) {
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
   * Returns a string representation of clause
   * starting at start.
   */
  private String clauseToString(int start) {
    StringBuffer string = new StringBuffer();
    string.append("{");
    while (true) {
      int literal = clauses.get(start++);
      if (literal == 0) break;
      if (literal == REMOVED) continue;
      string.append(literal);
      string.append(", ");
    }
    string.append("}");
    return string.toString();
  }

  /**
   * Verifies lengths.
   */
  private void verifyLengths() {
    TIntIntIterator it = lengths.iterator();
    for (int size = lengths.size(); size > 0; size--) {
      it.advance();
      int start = it.key();
      int length = 0;

      for (int end = start; ; end++) {
        int literal = clauses.get(end);
        if (literal == 0) break;
        if (literal == REMOVED) continue;

        length++;
        assert !isAssigned(literal) 
            : "Literal " + literal + " is assigned";
        assert watchList(literal).contains(start) 
            : "Clause " + start + " not in watch list of " + literal;
      }
      assert it.value() == length
          : "Clause " + start + " has wrong length";
    }
  }

  /**
   * Verifies watch lists.
   */
  private void verifyWatchLists() {
    for (int u = -numVariables; u <= numVariables; ++u) {
      if (u == 0) continue;
      TIntIterator it = watchList(u).iterator();
      for (int size = watchList(u).size(); size > 0; size--) {
        int start = it.next();
        assert lengths.containsKey(start) 
            : "Watch list of " + u + " contains satisfied clause";
        findLiteral(start, u);  // NOTE: uses findLiteral's internal check
      }
    }
  }

  /**
   * Verifies that assigned instances are removed.
   */
  private void verifyAssigned() {
    for (int u = -numVariables; u <= numVariables; ++u) {
      if (u != 0 && isAssigned(u)) {
        assert watchList(u).isEmpty()
            : "Assigned literal " + u + " has non empty watch list";
        assert dag.neighbours(u) == null
            : "Assigned literal " + u + " is in dag";
      }
    }
  }

  /**
   * Checks solver for consistency.
   */
  private void verify() {
    if (Configure.enableExpensiveChecks) {
      verifyLengths();
      verifyWatchLists();
      verifyAssigned();
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
    return skeleton().toString();
  }

  /**
   * Returns a normalized literal to branch on.
   *
   * @param branch br
   */
  public Solution solve(final int branch) {
    buildWatchLists();
    if (branch != 0) {
      addUnit(branch);
    }

    // int beforeNumLiterals = clauses.size();

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

    TIntArrayList compact = cleanClauses();
    if (compact.size() == 0) {
      // Solves the remaining 2SAT encoded in the implication graph
      for (TIntIterator it = dag.solve().iterator(); it.hasNext(); ){
        int unit = it.next();
        if (!units.contains(unit)) addUnit(unit);
      }
      return Solution.satisfiable(units.elements(), proxies);
    }

    // Gets the core instance that needs to be solved further
    Skeleton core = new Skeleton();
    core.append(dag.skeleton());
    core.append(compact);
    int newBranch = chooseBranch();

    // int afterNumLiterals = core.clauses.size();
    // logger.info("Reduced from " + beforeNumLiterals + " to " + afterNumLiterals
                // + " (diff " + (beforeNumLiterals - afterNumLiterals) + ")");
    return Solution.unknown(units.elements(), proxies, core, newBranch);
  }

  /**
   * A score for branch.
   */
  private double score(int branch) {
    int num = numBinaries(branch);
    TIntHashSet neighbours = dag.neighbours(branch);
    if (neighbours != null) {
      TIntIterator it = neighbours.iterator();
      for (int size = neighbours.size(); size > 0; size--) {
        int literal = it.next();
        num += watchList(literal).size();
      }
    }
    return Math.pow(random.nextDouble(), num);
  }

  // The idea here is that if a literal is frequent
  // it will have a higher chance to be selected
  private int chooseBranch() {
    int bestBranch = 0;
    double bestValue = Double.POSITIVE_INFINITY;
    for (int branch = 1; branch <= numVariables; ++branch) {
      if (!isAssigned(branch)) {
        double value = score(branch) * score(-branch);
        if (value < bestValue) {
          bestBranch = branch;
          bestValue = value;
        }
      }
    }
    assert bestBranch != 0;
    return bestBranch;
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

    if (Configure.binarySelfSubsumming) {
      binarySelfSubsumming();
      propagate();
    }

    if (Configure.subsumming) {
      subsumming();
      propagate();
    }

    if (Configure.pureLiterals) {
      pureLiterals();
      propagate();
    }

    verify();
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
      assert 0 <= length && length < 3
          : "Invalid clause of length " + length + " in queue";
      int position = start;

      switch (length) {
        case 0: {
          throw new ContradictionException();
        }

        case 1: {  // unit
          int u;
          while ((u = clauses.get(position)) == REMOVED) {
            position++;
          }
          addUnit(u);
          assert isSatisfied(position);
          break;
        }

        case 2: {  // binary
          int u, v;
          while ((u = clauses.get(position)) == REMOVED) {
            position++;
          }
          position++;
          while ((v = clauses.get(position)) == REMOVED) {
            position++;
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
    int[] counts = new int[2 * numVariables + 1];
    int[] sums = new int[2 * numVariables + 1];
    int[] touched = new int[2 * numVariables + 1];
    int numUnits = 0, numBinaries = 0;

    for (int start : lengths.keys()) {
      int numLiterals = 0;
      int clauseSum = 0;
      int numTouched = 0;

      for (int end = start; end < clauses.size(); end++) {
        int literal = clauses.get(end);
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
        if (isSatisfied(start)) continue;
        int touch = touched[i];
        int literal = mapNtoZ(touch);
        if (isAssigned(literal)) continue;

        if (counts[touch] == numLiterals) {
          // There is an edge from literal to all literals in clause.
          if (!units.contains(-literal)) {
            addUnit(-literal);
            ++numUnits;
          }
        } else if (counts[touch] + 1 == numLiterals) {
          // There is an edge from literal to all literals in clause except one.
          int missing = clauseSum - sums[touch];
          if (!isAssigned(missing) && !dag.containsEdge(literal, missing)) {
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
    if (numUnits > 0) System.err.print("hu" + numUnits + ".");
    if (numBinaries > 0) System.err.print("hb" + numBinaries + ".");
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

    if (numUnits > 0) System.err.print("p" + numUnits + ".");
    return numUnits > 0;
  }

  /**
   * Binary self subsumming.
   */
  public boolean binarySelfSubsumming() {
    int numSatisfiedClauses = 0, numRemovedLiterals = 0;

    for (int start : lengths.keys()) {
      // Finds the end of the clause
      int end = start - 1;
      while (clauses.get(++end) != 0) { }

    search:
      for (int i = start; i < end; ++i) {
        int first = clauses.get(i);
        if (first == REMOVED) continue;

        TIntHashSet neighbours = dag.neighbours(-first);
        if (neighbours == null) continue;

        for (int j = start; j < end; ++j) {
          int second = clauses.get(j);
          if (i == j || second == REMOVED) continue;

          if (neighbours.contains(-second)) {
            // If a + b + c + ... and -a => -b
            // then a + c + ...
            removeLiteral(start, second);
            ++numRemovedLiterals;
            continue;
          }

          if (neighbours.contains(second)) {
            // If a + b + c + ... and -a => b
            // then clause is tautology
            removeClause(start);
            ++numRemovedLiterals;
            break search;
          }
        }
      }

      start = end + 1;
    }

    if (numRemovedLiterals > 0) System.err.print("bl" + numRemovedLiterals + ".");
    if (numSatisfiedClauses > 0) System.err.print("bc" + numSatisfiedClauses + ".");
    return numRemovedLiterals > 0 || numSatisfiedClauses > 0;
  }

  /**
   * Returns a hash of a.
   * This function is better than the one provided by
   * gnu.trove.HashUtils which is the same as the one
   * provided by the Java library.
   *
   * Robert Jenkins' 32 bit integer hash function
   * http://burtleburtle.net/bob/hash/integer.html
   *
   * @param a integer to hash
   * @return a hash code for the given integer
   */
  private static int hash(int a) {
    a = (a + 0x7ed55d16) + (a << 12);
    a = (a ^ 0xc761c23c) ^ (a >>> 19);
    a = (a + 0x165667b1) + (a << 5);
    a = (a + 0xd3a2646c) ^ (a << 9);
    a = (a + 0xfd7046c5) + (a << 3);
    a = (a ^ 0xb55a4f09) ^ (a >>> 16);
    return a;
  }

  /**
   * Subsumming.
   */
  public void subsumming() {
    int[] keys = lengths.keys();

    // Computes clause hashes
    TIntLongHashMap hashes = new TIntLongHashMap();
    for (int i = 0; i < keys.length; ++i) {
      int start = keys[i];
      long hash = 0;
      for (int j = start; ; j++) {
        int literal = clauses.get(j);
        if (literal == 0) break;
        if (literal == REMOVED) continue;
        hash |= 1 << (hash(literal) >>> 26);
      }
      hashes.put(start, hash);
    }

    // Finds subsummed clauses.
    int numTries = 0, numGood =0;
    int numRemovedClauses = 0;
    for (int i = 0; i < keys.length; ++i) {
      int start = keys[i];
      if (isSatisfied(start)) continue;
      long startHash = hashes.get(start);

      // Finds literal included in minimum number of clauses
      int bestLiteral = 0;
      int minNumClauses = Integer.MAX_VALUE;
      for (int j = start; ; j++) {
        int literal = clauses.get(j);
        if (literal == 0) break;
        if (literal == REMOVED) continue;
        int numClauses = watchList(literal).size();
        if (numClauses < minNumClauses) {
          bestLiteral = literal;
          minNumClauses = numClauses;
        }
      }

      if (watchList(bestLiteral).size() <= 1) continue;
      for (int clause : watchList(bestLiteral).toArray()) {
        if (clause == start) continue;
        if ((startHash & hashes.get(clause)) != startHash) continue;

        boolean good = true;
        for (int j = start; good; j++) {
          int literal = clauses.get(j);
          if (literal == 0) break;
          if (literal == REMOVED) continue;
          good = watchList(literal).contains(clause);
        }

        if (good) {
          ++numRemovedClauses;
          removeClause(clause);
        }
      }
    }

    if (numRemovedClauses > 0) System.err.print("ss" + numRemovedClauses + ".");
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
      int literal = clauses.get(c);
      assert u == 0 || literal != 0 
          : "Literal " + u + " is missing from clause";
      if (u == literal) return c;
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
    assert isSatisfied(clause);
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
    assert !isAssigned(u) : "Unit " + u + " already assigned";

    if (dag.hasNode(u)) {
      int[] neighbours = dag.neighbours(u).toArray();
      for (int unit : neighbours) {
        propagateUnit(unit);
        units.add(unit);
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
    assert !isAssigned(u) : "First literal " + u + " is assigned";
    assert !isAssigned(v) : "Second literal " + v + " is assigned";

    // propagates contradictions contradictions
    TIntHashSet contradictions = dag.findContradictions(-u, v);
    if (!contradictions.isEmpty()) {
      int[] contradictions_ = contradictions.toArray();
      for (int unit : contradictions_) {
        assert !units.contains(-unit);
        if (!units.contains(unit)) addUnit(unit);
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
        int literal = it.next();
        renameLiteral(literal, join.parent);
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
        watchList(u).remove(clause);
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
