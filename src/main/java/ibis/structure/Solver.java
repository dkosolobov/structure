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

  /**
   * An iterator over Solver clauses.
   */
  private final class ClauseIterator {
    private int[] colors;
    private int currentColor = 0;
    private int oldStart, oldEnd = -1;
    private int newStart, newEnd = 0;
    private boolean simplified = false;

    public ClauseIterator() {
      colors = new int[2 * numVariables + 1];
    }

    /**
     * Returns the begining of the clause.
     */
    public int start() {
      return newStart;
    }

    /**
     * Returns one past the end of the clause.
     */
    public int end() {
      return newEnd - 1;
    }

    /**
     * Returns true if the clause was simplified.
     * Call valid only if iterations was completed.
     */
    public boolean simplified() {
      return simplified;
    }

    /**
     * Iterates throw the clauses in solver.
     * Does unit and renames propagation.
     * The returned clauses have at least three literals.
     *
     * @return false if iteration ended.
     */
    public boolean next() throws ContradictionException {
      while (true) {
        // Change the marking color for visited literals.
        currentColor += 1;

        // Find end of the next clause
        oldStart = ++oldEnd;
        while (oldEnd < clauses.size() && clauses.get(oldEnd) != 0) {
          ++oldEnd;
        }
        if (oldEnd == clauses.size()) {
          if (newEnd != clauses.size()) {
            clauses.remove(newEnd, clauses.size() - newEnd);
            simplified = true;
          }
          return false;
        }

        // Propagates units and renames
        boolean satisfied = false;
        for (int i = oldStart; i < oldEnd; ++i) {
          int literal = clauses.get(i);
          if (literal == REMOVED) {
            continue;
          }
          literal = getRecursiveProxy(clauses.get(i));
          if (units.contains(-literal)) {
            clauses.set(i, REMOVED);
            continue;
          }
          if (units.contains(literal)) {
            // literal is a unit
            satisfied = true;
            break;
          }
          if (colors[BitSet.mapZtoN(literal)] == currentColor) {
            // literal is in the same clause
            clauses.set(i, REMOVED);
            continue;
          }
          if (colors[BitSet.mapZtoN(-literal)] == currentColor) {
            // -literal is in the same clause
            satisfied = true;
            break;
          }
          colors[BitSet.mapZtoN(literal)] = currentColor;
          clauses.set(i, literal);
        }

        // Satisfied clauses are removed completely
        if (satisfied) {
          continue;
        }

        // Removes all REMOVED and compacts clauses
        newStart = newEnd;
        for (int i = oldStart; i <= oldEnd; ++i) {
          int literal = clauses.get(i);
          if (literal != REMOVED) {
            clauses.set(newEnd++, literal);
          }
        }

        int length = newEnd - newStart - 1;
        if (length == 0) {
          throw new ContradictionException();
        } else if (length == 1) {
          newEnd = newStart;
          addUnit(clauses.get(newStart));
        } else if (length == 2) {
          newEnd = newStart;
          addBinary(clauses.get(newStart), clauses.get(newStart + 1));
        } else {
          return true;
        }
      }
    }
  }

  // number of variables
  private int numVariables;
  // The set of true literals discovered.
  private BitSet units = new BitSet();
  // Stores equalities between literals.
  private int[] proxies;
  // The implication graph.
  private DAG dag;
  // List of clauses separated by 0.
  private TIntArrayList clauses;
  private int numBacktrackCalls = 0;

  public Solver(final Skeleton instance, final int branch) {
    numVariables = instance.numVariables;
    if (branch != 0) units.add(branch);
    dag = new DAG(numVariables);
    clauses = (TIntArrayList) instance.clauses.clone();

    proxies = new int[numVariables + 1];
    for (int literal = 1; literal <= numVariables; ++literal) {
      proxies[literal] = literal;
    }

    // logger.info("Solving " + clauses.size() + " literals and "
                // + numVariables + " variables, branching on " + branch);
    System.err.print(".");
    System.err.flush();
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
    skeleton.append(clauses);

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
   * Returns number of neighbours in DAG for node
   * or 0 if node is not in DAG.
   */
  private int numBinaries(int node) {
    TIntHashSet neighbours = dag.neighbours(-node);
    return neighbours == null ? 0 : neighbours.size() - 1;
  }

  private static java.util.Random random = new java.util.Random(1);

  /**
   * Returns a normalized literal to branch on.
   */
  public Solution solve() {
    int beforeNumLiterals = clauses.size();

    int solved;
    try {
      simplify();
      solved = backtrack();
    } catch (ContradictionException e) {
      solved = Solution.UNSATISFIABLE;
    }

    if (solved == Solution.UNSATISFIABLE) {
      return Solution.unsatisfiable();
    }

    // Keeps only positive literals
    for (int literal = 1; literal <= numVariables; ++literal) {
      getRecursiveProxy(literal);
    }
    if (solved == Solution.SATISFIABLE) {
      return Solution.satisfiable(units.elements(), proxies);
    }

    // Gets the core instance that needs to be solved further
    Skeleton core = new Skeleton();
    core.append(dag.skeleton());
    core.append(clauses);

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
   * Attempts to find a solution by backtracking.
   */
  private int backtrack() {
    if (clauses.size() > BACKTRACK_THRESHOLD) {
      // Clause is too difficult to solve.
      return Solution.UNKNOWN;
    }
    
    int[] assigned = new int[2 * numVariables + 1];
    int solved = backtrackHelper(0, assigned);

    if (solved == Solution.UNKNOWN) {
      logger.info("Backtracking reached maximum number of calls");
      return Solution.UNKNOWN;
    }
    if (solved == Solution.UNSATISFIABLE) {
      logger.info("Backtracking found a contradiction");
      return Solution.UNSATISFIABLE;
    }
    logger.info("Backtracking found a solution");

    // Propagates the satisfying assignment
    for (int literal = -numVariables; literal <= numVariables; ++literal) {
      if (literal != 0 && assigned[BitSet.mapZtoN(literal)] > 0) {
        assert assigned[BitSet.mapZtoN(-literal)] == 0;
        addUnit(literal);
      }
    }
    // Solves the remaining 2SAT encoded in the implication graph
    for (TIntIterator it = dag.solve().iterator(); it.hasNext(); ){
      addUnit(it.next());
    }

    try {
      propagate();
    } catch (ContradictionException e) {
      assert false: "Backtracking found a contradicting solution";
      return Solution.UNSATISFIABLE;
    }

    assert clauses.size() == 0;
    return Solution.SATISFIABLE;
  }

  /**
   * Adds delta to all neighbours of literal.
   */
  private static void adjustNeighbours(
      final int literal, final TIntHashSet neighbours,
      final int[] assigned, final int delta) {
    if (neighbours != null) {
      TIntIterator it = neighbours.iterator();
      for (int size = neighbours.size(); size > 0; --size) {
        assigned[BitSet.mapZtoN(it.next())] += delta;
      }
    } else {
      assigned[BitSet.mapZtoN(literal)] += delta;
    }
  }

  /**
   * Does a backtracking.
   *
   * @param start the begin of the clause to satisfy
   * @param assigned a map from literal to a counter. If counter
   *                 is greater than zero literal was assigned.
   * @return type of the solution found (SATISFIABLE, UNSATISFIABLE, UNKNOWN)
   */
  private int backtrackHelper(final int start, final int[] assigned) {
    if (start == clauses.size()) {
      // Assignment satisfied all clauses
      return Solution.SATISFIABLE;
    }
    if (numBacktrackCalls == BACKTRACK_MAX_CALLS) {
      // Solution.UNKNOWN marks the end of the computation
      return Solution.UNKNOWN;
    }
    ++numBacktrackCalls;

    // Finds end of clause
    int end = start - 1;
    boolean satisfied = false;
    while (true) {
      int literal = clauses.get(++end);
      if (literal == 0) {
        break;
      }
      assert assigned[BitSet.mapZtoN(literal)] >= 0;
      if (assigned[BitSet.mapZtoN(literal)] > 0) {
        satisfied = true;
      }
    }
    assert clauses.get(end) == 0;
    // Skip already satisfied clauses
    if (satisfied) {
      return backtrackHelper(end + 1, assigned);
    }

    // Tries each unassigned literal
    for (int i = start; i < end; ++i) {
      int literal = clauses.get(i);
      assert literal != 0;
      if (assigned[BitSet.mapZtoN(-literal)] == 0) {
        assert !units.get(literal);
        TIntHashSet neighbours = dag.neighbours(literal);
        adjustNeighbours(literal, neighbours, assigned, 1);
        int solved = backtrackHelper(end + 1, assigned);
        if (solved == Solution.SATISFIABLE) {
          return Solution.SATISFIABLE;
        }
        if (solved == Solution.UNKNOWN) {
          return Solution.UNKNOWN;
        }
        adjustNeighbours(literal, neighbours, assigned, -1);
      }
    }

    return Solution.UNSATISFIABLE;
  }

  /**
   * Simplifies the instance.
   */
  public void simplify() throws ContradictionException {
    while (propagate());
    // while (hyperBinaryResolution());
    subSumming();
    // binarySelfSubsumming();
    // propagate();
    pureLiterals();
  }

  /**
   * Tests if clause starting at startFirst is contained
   * in clause at startSecond.
   */
  private boolean contained(int startFirst, int startSecond) {
    for (int indexFirst = 0; ; ++indexFirst) {
      int literalFirst = clauses.get(startFirst + indexFirst);
      if (literalFirst == 0) {
        return true;
      }

      for (int indexSecond = 0; ; ++indexSecond) {
        int literalSecond = clauses.get(startSecond + indexSecond);
        if (literalSecond == 0) {
          return false;
        }
        if (literalFirst == literalSecond) {
          break;
        }
      }
    }
  }

  /**
   * Returns a hash of a.
   * This function is better than the one provided by
   * gnu.trove.HashUtils which is the same as the one
   * provided by the Java library.
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
   * Removes all clauses for which there exist
   * another clause included.
   *
   * @return true if any clause was removed.
   */
  public boolean subSumming() {
    // logger.debug("Running subSumming()");
    final int numIndexBits = 8;  // must be a POT
    final int indexMask = numIndexBits - 1;

    TIntArrayList[] sets = new TIntArrayList[1 << numIndexBits];
    for (int i = 0; i < (1 << numIndexBits); ++i) {
      sets[i] = new TIntArrayList();
    }
    TIntArrayList starts = new TIntArrayList();
    TLongArrayList hashes = new TLongArrayList();
    int numClauses = 0;
    int start = 0;
    int clauseIndex = 0;
    long clauseHash = 0;

    // Puts every clause in a set to reduce the number of
    // pairs to be checked.
    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.get(i);
      if (literal == 0) {
        sets[clauseIndex].add(numClauses);
        starts.add(start);
        hashes.add(clauseHash);
        ++numClauses;
        start = i + 1;
        clauseHash = 0;
        clauseIndex = 0;
      } else {
        // Bob Jenkins' mix64()
        // http://burtleburtle.net/bob/c/lookup8.c
        long a = literal, b = literal, c = 0x9e3779b97f4a7c13L;
        a -= b; a -= c; a ^= (c>>43);
        b -= c; b -= a; b ^= (a<<9);
        c -= a; c -= b; c ^= (b>>8);
        a -= b; a -= c; a ^= (c>>38);
        b -= c; b -= a; b ^= (a<<23);
        c -= a; c -= b; c ^= (b>>5);
        a -= b; a -= c; a ^= (c>>35);
        b -= c; b -= a; b ^= (a<<49);
        c -= a; c -= b; c ^= (b>>11);
        a -= b; a -= c; a ^= (c>>12);
        b -= c; b -= a; b ^= (a<<18);
        c -= a; c -= b; c ^= (b>>22);

        clauseIndex |= 1 << (c & indexMask);
        // The following idea was suggested by Warren Schudy at:
        // http://cstheory.stackexchange.com/questions/1786/hashing-sets-of-integers
        clauseHash |= a & b & c;
      }
    }
    starts.add(start);  // Add a sentinel.

    if (logger.getEffectiveLevel().toInt() <= Level.TRACE_INT) {
      // Prints an histogram
      TIntArrayList histogram = new TIntArrayList();
      for (int i = 0; i < (1 << numIndexBits); ++i) {
        histogram.add(sets[i].size());
      }
      logger.trace("Histogram is " + histogram);
    }

    long numPairs = 0, numTests = 0, numHits = 0;
    boolean simplified = false;
    for (int first = 0; first < (1 << numIndexBits); ++first) {
      for (int second = first; second < (1 << numIndexBits); ++second) {
        if ((first & second) != first) {
          continue;
        }
        numPairs += (long) sets[first].size() * sets[second].size();

        for (int i = 0; i < sets[first].size(); ++i) {
          final int indexFirst = sets[first].getQuick(i);
          final int startFirst = starts.getQuick(indexFirst);
          final long hashFirst = hashes.getQuick(indexFirst);

          for (int j = 0; j < sets[second].size(); ++j) {
            final int indexSecond = sets[second].getQuick(j);
            final int startSecond = starts.getQuick(indexSecond);
            final long hashSecond = hashes.getQuick(indexSecond);

            if (indexFirst == indexSecond) {
              continue;
            }
            if ((hashFirst & hashSecond) != hashFirst) {
              continue;
            }
            ++numTests;
            if (contained(startFirst, startSecond)) {
              ++numHits;
              simplified = true;
              // Removes sets[second][j] by replacing it
              // with the last element.
              starts.set(indexSecond, -1);
              int last = sets[second].size() - 1;
              sets[second].set(j, sets[second].get(last));
              sets[second].remove(last);
              --j;
            }
          }
        }
      }
    }
    logger.debug("Tested " + numPairs + " pairs out of "
                 + ((long) numClauses * numClauses) + " ("
                 + ((double) numPairs / numClauses / numClauses) + ")");
    logger.debug("Hit rate " + (100. * numHits / numTests) + "; "
                 + numTests + " tests");

    // Removes sub-summed clauses.
    int pos = 0, startsPos = 0;
    boolean removed = starts.get(startsPos) == -1;
    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.get(i);
      if (!removed) {
        clauses.set(pos++, literal);
      }
      if (literal == 0) {
        removed = starts.get(++startsPos) == -1;
      }
    }
    logger.debug("Sub-summing removed " + (clauses.size() - pos)
                  + " literals");
    if (pos < clauses.size()) {
      // BUG: TIntArrayList.remove() raises ArrayIndexOutOfBoundsException
      // if pos == clauses.size()
      clauses.remove(pos, clauses.size() - pos);
    }
    return simplified;
  }

  /**
   * Propagates units and binary clauses (one pass only).
   *
   * @return true if instances was simplified
   */
  public boolean propagate() throws ContradictionException {
    // logger.info("Running propagate()");
    ClauseIterator cit = new ClauseIterator();
    while (cit.next()) {}
    return cit.simplified();
  }

  /**
   * Hyper-binary resolution.
   *
   * If (a1 + ... ak + b) and (l &ge; -a1) ... (l &ge; -ak)
   * then l &ge; b, otherwise if l then clause is contradiction
   *
   * @return true if instances was simplified
   */
  public boolean hyperBinaryResolution() throws ContradictionException {
    // logger.debug("Running hyperBinaryResolution()");
    ClauseIterator cit = new ClauseIterator();

    int[] counts = new int[2 * numVariables + 1];
    int[] sums = new int[2 * numVariables + 1];
    int[] touched = new int[2 * numVariables + 1];
    int numUnits = 0, numBinaries = 0;

    while (cit.next()) {
      int start = cit.start(), end = cit.end();
      int numLiterals = end - start;
      int clauseSum = 0;
      int numTouched = 0;

      for (int i = start; i < end; ++i) {
        int literal = clauses.get(i);
        clauseSum += literal;
        TIntHashSet neighbours = dag.neighbours(literal);
        if (neighbours != null) {
          TIntIterator it = neighbours.iterator();
          for (int size = neighbours.size(); size > 0; --size) {
            int node = BitSet.mapZtoN(-it.next());
            if (counts[node] == 0) {
              touched[numTouched++] = node;
            }
            counts[node] += 1;
            sums[node] += literal;
          }
        }
      }

      for (int i = 0; i < numTouched; ++i) {
        int touch = touched[i];
        int literal = BitSet.mapNtoZ(touch);

        if (counts[touch] == end - start) {
          // there is an edge from literal to all literals in clause
          if (!units.contains(-literal)) {
            addUnit(-literal);
            ++numUnits;
          }
        } else if (counts[touch] + 1 == end - start) {
          // there is an edge from literal to all literals in clause except one
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
    return numUnits > 0 || numBinaries > 0 || cit.simplified();
  }

  /**
   * Pure literal assignment.
   *
   * @return true if instances was simplified
   */
  public boolean pureLiterals() throws ContradictionException {
    // logger.debug("Running pureLiterals()");

    // Marks existing literals in clauses
    BitSet bs = new BitSet();
    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.get(i);
      if (literal != 0) bs.set(literal);
    }

    // Marks existing literals in binaries
    for (int u : dag.nodes()) {
      if (dag.neighbours(u).size() > 1) {
        bs.set(-u);
      }
    }

    // 0 is in bs, but -0 is also so
    // 0 will not be considered a pure literal.
    int numUnits = 0;
    for (int literal : bs.elements()) {
      if (!bs.get(-literal)) {  // is pure literal
        if (units.contains(literal) && !units.contains(-literal)) {  // not satisfied
          addUnit(literal);
          ++numUnits;
        }
      }
    }

    // Propagates discovered units
    if (numUnits > 0) {
      logger.info("Discovered " + numUnits + " pure literal(s)");
      propagate();
      return true;
    }
    return false;
  }

  /**
   * Binary self subsumming.
   */
  public boolean binarySelfSubsumming() {
    int numSatisfiedClauses = 0, numRemovedLiterals = 0;

    int start = 0;
    while (start < clauses.size()) {
      // Finds the end of the clause
      int end = start - 1;
      while (clauses.get(++end) != 0) { }

      boolean satisfied = false;

    search:
      for (int i = start; i < end; ++i) {
        int first = clauses.get(i);
        if (first == REMOVED) {
          continue;
        }

        TIntHashSet neighbours = dag.neighbours(-first);
        if (neighbours == null) {
          continue;
        }

        for (int j = start; j < end; ++j) {
          int second = clauses.get(j);
          if (i == j || second == REMOVED) {
            continue;
          }

          if (neighbours.contains(-second)) {
            // If a + b + c + ... and -a => -b
            // then a + c + ...
            clauses.set(j, REMOVED);
            ++numRemovedLiterals;
            continue;
          }

          if (neighbours.contains(second)) {
            // If a + b + c + ... and -a => b
            // then clause is tautology
            satisfied = true;
            break search;
          }
        }
      }

      if (satisfied) {
        numRemovedLiterals += end - start;
        ++numSatisfiedClauses;
        for (int i = start; i <= end; ++i) {
          clauses.set(i, REMOVED);
        }
      }

      start = end + 1;
    }

    // logger.debug("Removed " + numRemovedLiterals + " literals and found "
    //              + numSatisfiedClauses + " satisfied clauses");
    return numRemovedLiterals > 0;
  }

  /**
   * Adds a new unit.
   *
   * @param u unit to add.
   */
  private void addUnit(final int u) {
    assert u != 0 && !units.get(-u);
    // logger.info("Found unit " + u);
    if (dag.hasNode(u)) {
      int[] neighbours_ = dag.neighbours(u).toArray();
      units.addAll(neighbours_);
      dag.delete(neighbours_);
    } else {
      units.add(u);
    }
  }

  /**
   * Adds a new binary
   *
   * @param u first literal
   * @param v second literal
   */
  private void addBinary(final int u, final int v) {
    // logger.info("Found binary " + u + " or " + v);
    TIntHashSet contradictions = dag.findContradictions(-u, v);
    if (!contradictions.isEmpty()) {
      int[] contradictions_ = contradictions.toArray();
      units.addAll(contradictions_);
      dag.delete(contradictions_);
      if (contradictions.contains(u) || contradictions.contains(v)) {
        return;
      }
    }

    DAG.Join join = dag.addEdge(-u, v);
    if (join != null) {
      TIntIterator it;
      for (it = join.children.iterator(); it.hasNext();) {
        int node = it.next();
        assert node != 0;
        if (node < 0) {
          proxies[-node] = -join.parent;
        } else {
          proxies[node] = join.parent;
        }
      }
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
