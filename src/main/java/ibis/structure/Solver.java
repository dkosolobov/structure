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
  private static final int BACKTRACK_THRESHOLD = 1 << 8;
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

  private static java.util.Random random = new java.util.Random(1);

  // number of variables
  private int numVariables;
  // The set of true literals discovered.
  private BitSet units = new BitSet();
  // Stores the normalization
  public int[] variableMap;
  // Stores equalities between literals.
  private int[] proxies;
  // The implication graph.
  private DAG dag;
  // List of clauses separated by 0.
  private TIntArrayList clauses;
  private int backtrackCalls = 0;

  public Solver(final Skeleton instance, final int branch) {
    normalize(instance, branch);
    proxies = new int[2 * numVariables + 1];
    dag = new DAG(numVariables);
    for (int literal = -numVariables; literal <= numVariables; ++literal) {
      if (literal != 0) {
        proxies[BitSet.mapZtoN(literal)] = literal;
      }
    }
  }

  /**
   *
   */
  private void normalize(Skeleton instance, int branch) {
    clauses = new TIntArrayList(instance.clauses.size());
    TIntIntHashMap map = new TIntIntHashMap();
    for (int i = 0; i < instance.clauses.size(); ++i) {
      int literal = instance.clauses.get(i);
      if (literal == 0) {
        clauses.add(0);
        continue;
      }

      if (!map.contains(literal)) {
        int newName = (map.size() / 2) + 1;
        map.put(literal, newName);
        map.put(-literal, -newName);
      }
      clauses.add(map.get(literal));
    }

    numVariables = map.size() / 2;
    if (branch != 0) {
      units.add(map.get(branch));
    }

    // Constructs the inverse of map, variableMap
    variableMap = new int[numVariables + 1];
    for (int literal : map.keys()) {
      int name = map.get(literal);
      if (name > 0) {
        variableMap[name] = literal;
      }
    }
  }

  /**
   * Denormalizes one literal.
   */
  private int denormalize(int literal) {
    if (literal > 0) {
      return variableMap[literal];
    } else if (literal < 0) {
      return -variableMap[-literal];
    } else {
      return 0;
    }
  }

  /**
   * Denormalizes all literals in array.
   */
  private void denormalize(final TIntArrayList array) {
    for (int i = 0; i < array.size(); ++i) {
      array.set(i, denormalize(array.get(i)));
    }
  }

  /**
   * Returns current (simplified) instance.
   *
   * Includes units and equivalent literals.
   *
   * @return a denormalized skeleton
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

    denormalize(skeleton.clauses);
    return skeleton;
  }

  /**
   * Returns the current core (simplified) instance.
   *
   * Doesn't include units or equivalent literals.
   * TODO: Removes mutexes.
   *
   * @param branch
   * @return a normalized skeleton
   */
  private Solution getSolution(int branch) {
    // Keeps only positive literals
    int[] proxies = new int[numVariables + 1];
    for (int literal = 1; literal <= numVariables; ++literal) {
      proxies[literal] = getRecursiveProxy(literal);
    }

    // Gets the core instance that needs to be solved further
    Skeleton skeleton = null;
    if (branch != 0) {
      skeleton = new Skeleton();
      skeleton.append(dag.skeleton());
      skeleton.append(clauses);
    }

    return new Solution(variableMap, units.elements(),
                        proxies, skeleton, branch);
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
    return neighbours == null ? 0 : neighbours.size();
  }

  /**
   * Returns a normalized literal to branch on.
   */
  public Solution solve() throws ContradictionException {
    simplify();
    if (clauses.size() <= BACKTRACK_THRESHOLD) {
      if (backtrack()) {
        return getSolution(0);
      }
    }

    // The idea here is that if a literal is frequent
    // it will have a higher chance to be selected
    int bestLiteral = 0, bestValue = Integer.MIN_VALUE;
    TIntHashSet literals = new TIntHashSet();
    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.get(i);
      if (literal != 0) {
        int value = random.nextInt();
        if (value > bestValue) {
          bestLiteral = literal;
          bestValue = value;
        }
      }
    }

    return getSolution(bestLiteral);
  }

  /**
   * Attempts to find a solution by backtracking.
   */
  private boolean backtrack() throws ContradictionException {
    long startTime = System.currentTimeMillis();
    TIntIntHashMap assigned = new TIntIntHashMap();
    boolean solved = backtrackHelper(0, assigned);
    long endTime = System.currentTimeMillis();
    logger.info("Backtracking " + clauses.size() + " literals took "
                + (endTime - startTime) / 1000. + " using "
                + backtrackCalls + " calls");

    if (backtrackCalls == BACKTRACK_MAX_CALLS) {
      logger.info("Backtracking readched maximum number of calls");
      return false;
    }
    if (!solved) {
      logger.info("Backtracking found a contradiction");
      throw new ContradictionException();
    }
    logger.info("Backtracking found a solution");

    for (TIntIntIterator it = assigned.iterator(); it.hasNext(); ) {
      it.advance();
      if (it.value() > 0) {
        addUnit(it.key());
      }
    }
    for (TIntIterator it = dag.solve().iterator(); it.hasNext(); ){
      addUnit(it.next());
    }
    propagate();
    assert clauses.size() == 0;
    return true;
  }

  /**
   * Adds delta to all neighbours of literal.
   */
  private static void adjustNeighbours(
      final int literal, final TIntHashSet neighbours,
      final TIntIntHashMap assigned, final int delta) {
    if (neighbours != null) {
      for (TIntIterator it = neighbours.iterator(); it.hasNext(); ) {
        assigned.adjustOrPutValue(it.next(), delta, delta);
      }
    } else {
      assigned.adjustOrPutValue(literal, delta, delta);
    }
  }

  /**
   * Does a backtracking.
   *
   * @param start is the begin of the clause to satisfy
   * @param assigned is a map from literal to a counter. If counter
   *                 is greater than zero literal was assigned.
   */
  private boolean backtrackHelper(int start, TIntIntHashMap assigned) {
    if (start == clauses.size()) {
      return true;
    }
    if (backtrackCalls == BACKTRACK_MAX_CALLS) {
      return false;
    }
    ++backtrackCalls;

    /* Finds end of clause */
    int end = start;
    while (clauses.get(end) != 0) {
      ++end;
    }

    /* If the clause is already satisfied don't backtrack */
    for (int i = start; i < end; ++i) {
      int literal = clauses.get(i);
      if (assigned.get(literal) > 0) {
        return backtrackHelper(end + 1, assigned);
      }
    }

    for (int i = start; i < end; ++i) {
      int literal = clauses.get(i);
      if (assigned.get(-literal) == 0) {
        TIntHashSet neighbours = dag.neighbours(literal);
        adjustNeighbours(literal, neighbours, assigned, 1);
        if (backtrackHelper(end + 1, assigned)) {
          return true;
        }
        adjustNeighbours(literal, neighbours, assigned, -1);
      }
    }

    return false;
  }

  /**
   * Simplifies the instance.
   */
  public void simplify() throws ContradictionException {
    while (hyperBinaryResolution());
    // subSumming();
    while (pureLiterals());
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
    BitSet bs = new BitSet();
    ClauseIterator cit = new ClauseIterator();
    while (cit.next()) {
      int start = cit.start(), end = cit.end();
      for (int i = start; i < end; ++i) {
        bs.set(clauses.get(i));
      }
    }
    for (int u : dag.nodes()) {
      if (dag.neighbours(u).size() > 1) {
        bs.set(-u);
      }
    }

    // 0 is in bs, but -0 is also so
    // 0 will not be considered a pure literal.
    int numUnits = 0;
    for (int literal : bs.elements()) {
      if (!bs.get(-literal) && !units.contains(literal)) {
        addUnit(literal);
        ++numUnits;
      }
    }

    // logger.debug("Discovered " + numUnits + " pure literal(s)");
    return numUnits > 0 || cit.simplified();
  }

  /**
   * Adds a new unit.
   *
   * @param u unit to add.
   */
  private void addUnit(final int u) {
    // logger.info("Found unit " + u);
    TIntHashSet neighbours = dag.neighbours(u);
    if (neighbours == null) {
      units.add(u);
    } else {
      int[] neighbours_ = neighbours.toArray();
      units.addAll(neighbours_);
      dag.delete(neighbours_);
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
        proxies[BitSet.mapZtoN(node)] = join.parent;
        proxies[BitSet.mapZtoN(-node)] = -join.parent;
      }
    }
  }

  /**
   * Recursively finds the proxy of u.
   * The returned value doesn't have any proxy.
   */
  private int getRecursiveProxy(final int u) {
    int u_ = BitSet.mapZtoN(u);
    if (proxies[u_] != u) {
      proxies[u_] = getRecursiveProxy(proxies[u_]);
    }
    return proxies[u_];
  }
}
