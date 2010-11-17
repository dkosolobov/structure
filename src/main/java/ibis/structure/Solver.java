package ibis.structure;

import java.text.DecimalFormat;
import gnu.trove.TIntArrayList;
import gnu.trove.TLongArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntIterator;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

public final class Solver {
  private static final Logger logger = Logger.getLogger(Solver.class);
  private static final int REMOVED = Integer.MAX_VALUE;
  private static final int BACKTRACK_THRESHOLD = 1 << 9;
  private static final int BACKTRACK_MAX_CALLS = 1 << 14;

  // The set of true literals discovered.
  private TIntHashSet units;
  // The implication graph.
  private DAG dag = new DAG();
  // Stores equalities between literals.
  private TIntIntHashMap proxies = new TIntIntHashMap();
  // List of clauses separated by 0.
  private TIntArrayList clauses;

  public Solver(Skeleton instance)
      throws ContradictionException {
    this(instance, 0);
  }

  public Solver(final Skeleton instance, final int branch)
      throws ContradictionException {
    clauses = (TIntArrayList) instance.clauses.clone();
    units = new TIntHashSet();
    if (branch != 0) {
      units.add(branch);
    }
  }

  /**
   * Returns the current instance.
   */
  public Skeleton skeleton(boolean includeUnits) {
    Skeleton skeleton = new Skeleton();
    if (includeUnits) {
      for (TIntIterator it = units.iterator(); it.hasNext();) {
        skeleton.addArgs(it.next());
      }
    }
    for (int literal : proxies.keys()) {
      int proxy = getRecursiveProxy(literal);
      if (units.contains(proxy)) {
        if (includeUnits) {
          skeleton.addArgs(literal);
        }
      } else if (!units.contains(-proxy)) {
        skeleton.addArgs(literal, -proxy);
      }
    }
    skeleton.append(dag.skeleton());
    skeleton.append(clauses);
    return skeleton;
  }

  /**
   * Returns string representation of stored instance.
   */
  public String toString() {
    Skeleton skeleton = skeleton(true);
    skeleton.canonicalize();
    return skeleton.toString();
  }

  /**
   * Returns a list with all units including units from proxies.
   */
  public TIntArrayList getAllUnits() {
    TIntArrayList allUnits = new TIntArrayList();
    allUnits.add(units.toArray());
    for (int literal : proxies.keys()) {
      if (units.contains(getRecursiveProxy(literal))) {
        allUnits.add(literal);
      }
    }
    return allUnits;
  }

  static private double score(int positive2, int negative2,
                              int positive3, int negative3) {
    return 
      sigmoid(1 + positive2) + 8 * sigmoid(1 + positive3) +
      sigmoid(1 + negative2) + 8 * sigmoid(1 + negative3);
  }

  /**
   * Returns number of neighbours in DAG for node
   * or 0 if node is not in DAG.
   */
  private int numBinaries(int node) {
    TIntHashSet neighbours = dag.neighbours(node);
    return neighbours == null ? 0 : neighbours.size();
  }

  private static int numLookaheads = 0;
  static {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        logger.info("Number of lookaheads is " + numLookaheads);
      }
    });
  }
  
  public int lookahead() throws ContradictionException {
    synchronized (Job.class) {
      ++numLookaheads;
    }

    simplify();
    if (clauses.size() <= BACKTRACK_THRESHOLD) {
      if (backtrack()) {
        return 0;
      }
    }

    TIntIntHashMap counts = new TIntIntHashMap();
    for (int i = 0; i < clauses.size(); ++i) {
      final int literal = clauses.get(i);
      if (literal != 0) {
        counts.put(literal, counts.get(literal) + 1);
      }
    }

    int bestNode = 0;
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int node : counts.keys()) {
      if (node > 0) {
        double score = score(
            numBinaries(node), numBinaries(-node),
            counts.get(node), counts.get(-node));
        if (score > bestScore) {
          bestNode = node;
          bestScore = score;
        }
      }
    }

    assert bestNode != 0;
    return bestNode;
  }

  private int backtrackCalls = 0;

  private boolean backtrack() throws ContradictionException {
    long startTime = System.currentTimeMillis();
    TIntIntHashMap assigned = new TIntIntHashMap();
    boolean solved = backtrackHelper(0, assigned);
    long endTime = System.currentTimeMillis();
    logger.info("Backtracking " + clauses.size() + " literals took "
                + (endTime - startTime) / 1000. + " using "
                + backtrackCalls + " calls");

    if (backtrackCalls == BACKTRACK_MAX_CALLS) {
      return false;
    }
    if (!solved) {
      throw new ContradictionException();
    }

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
    while (clauses.getQuick(end) != 0) {
      ++end;
    }

    /* If the clause is already satisfied don't backtrack */
    for (int i = start; i < end; ++i) {
      int literal = clauses.getQuick(i);
      if (assigned.get(literal) > 0) {
        return backtrackHelper(end + 1, assigned);
      }
    }

    for (int i = start; i < end; ++i) {
      int literal = clauses.getQuick(i);
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
    logger.info("Simplyfing " + clauses.size() + " literal(s)");
    propagate();

    boolean simplified = true;
    while (simplified) {
      if (logger.getEffectiveLevel().toInt() <= Level.DEBUG_INT) {
        final DecimalFormat formatter = new DecimalFormat("#.###");
        final int numNodes = dag.numNodes();
        final int numEdges = dag.numEdges();
        logger.debug("DAG has " + numNodes + " nodes (sum of squares is "
                     + dag.sumSquareDegrees() + ") and " + numEdges
                     + " edges, " + formatter.format(1. * numEdges / numNodes)
                     + " edges/node on average");
        logger.debug("Simplifying " + clauses.size() + " literal(s), "
                    + numEdges + " binary(ies) and "
                    + getAllUnits().size() + " unit(s)");
      }

      simplified = propagate(hyperBinaryResolution());
    }

    subSumming();
    propagate(pureLiterals());
    propagateAll();

    logger.debug(clauses.size() + " literals left (excluding binaries "
                 + "and units)");
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
    logger.debug("Running subSumming()");
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

    TIntArrayList histogram = new TIntArrayList();
    for (int i = 0; i < (1 << numIndexBits); ++i) {
      histogram.add(sets[i].size());
    }
    // logger.debug("Histogram is " + histogram);

    long numPairs = 0, numTests = 0, numHits = 0;
    boolean simplified = false;
    for (int first = 0; first < (1 << numIndexBits); ++first) {
      for (int second = first; second < (1 << numIndexBits); ++second) {
        if ((first & second) != first) {
          continue;
        }
        numPairs += (long) sets[first].size() * sets[second].size();

        for (int i = 0; i < sets[first].size(); ++i) {
          final int indexFirst = sets[first].get(i);
          final int startFirst = starts.get(indexFirst);
          final long hashFirst = hashes.get(indexFirst);

          for (int j = 0; j < sets[second].size(); ++j) {
            final int indexSecond = sets[second].get(j);
            final int startSecond = starts.get(indexSecond);
            final long hashSecond = hashes.get(indexSecond);

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
   * Propagates every unit and every binary.
   */
  public boolean propagateAll() throws ContradictionException {
    boolean simplified = false;
    while (propagate()) {
      simplified = true;
    }
    return simplified;
  }

  /**
   * Appends and propagates new clauses.
   *
   * @param extraClauses extra clauses to propagate
   * @return true if instance was simplified
   */
  public boolean propagate(final TIntArrayList extraClauses)
      throws ContradictionException {
    if (extraClauses.size() > 0) {
      clauses.add(extraClauses.toNativeArray());
      return propagate();
    }
    return false;
  }

  /**
   * Propagates units and binary clauses (one pass only).
   *
   * @return true if instances was simplified
   */
  public boolean propagate() throws ContradictionException {
    logger.debug("Running propagate()");
    int start, end = clauses.size() - 1, pos = end;
    for (int i = end - 1; i >= 0; --i) {
      int literal = clauses.get(i);
      if (literal == 0 || i == 0) {
        start = i + (i != 0 ? 1 : 0);
        if (cleanClause(start)) {
          end = i;
          continue;
        }

        int length = 0;
        clauses.set(pos, 0);
        for (int j = end - 1; j >= start; --j) {
          int tmp = clauses.get(j);
          if (tmp != REMOVED) {
            clauses.set(pos - (++length), tmp);
          }
        }

        if (length == 0) {
          throw new ContradictionException();
        } else if (length == 1) {
          addUnit(clauses.get(pos - 1));
        } else if (length == 2) {
          addBinary(clauses.get(pos - 1), clauses.get(pos - 2));
        } else {
          pos -= length + 1;
        }

        end = i;
      }
    }

    if (pos != -1) {
      clauses.remove(0, pos + 1);
      return true;
    }
    return false;
  }

  private static double sigmoid(double x) {
    return (1 / (1 + Math.exp(-x)));
  }

  /**
   * Adds clause to clauses.
   */
  private static void pushClause(
      final TIntArrayList clauses, final int[] clause) {
    clauses.add(clause);
    clauses.add(0);
  }

  /**
   * Adds unit to clauses.
   */
  private static void pushClause(
      final TIntArrayList clauses, final int u0) {
    clauses.add(u0);
    clauses.add(0);
  }

  /**
   * Adds binary to clauses.
   */
  private static void pushClause(
      final TIntArrayList clauses, final int u0, final int u1) {
    clauses.add(u0);
    clauses.add(u1);
    clauses.add(0);
  }

  /**
   * Adds a new unit.
   *
   * @param u unit to add.
   */
  private void addUnit(final int u) {
    // logger.debug("Found unit " + u);
    TIntHashSet neighbours = dag.neighbours(u);
    if (neighbours == null) {
      units.add(u);
    } else {
      int[] neighbours_ = neighbours.toArray();
      units.addAll(neighbours_);
      dag.delete(neighbours_);
    }
  }

  private void addBinary(final int u, final int v) {
    // logger.debug("Found binary " + u + " or " + v);
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
        assert !proxies.contains(node) && !proxies.contains(-node);
        proxies.put(node, join.parent);
        proxies.put(-node, -join.parent);
      }
    }
  }

  /**
   * Cleans a clause and checks if it is trivialy satisfied.
   * Removes falsified literals or those proved to be extraneous.
   *
   * @param start position of the first literal in clause
   * @return true if the clause was satisfied
   */
  private boolean cleanClause(final int start) {
    // Renames literals to component.
    for (int i = start; ; ++i) {
      final int literal = clauses.get(i);
      if (literal == 0) {
        break;
      }
      assert literal != REMOVED;
      clauses.set(i, getRecursiveProxy(literal));
    }

    // Checks if the clause is satisfied, removes unsatisfied
    // literals and does binary resolution.
    for (int i = start; ; ++i) {
      final int literal = clauses.get(i);
      if (literal == 0) {
        return false;
      }
      if (literal == REMOVED) {
        continue;
      }
      if (units.contains(literal)) {
        return true;
      }
      if (units.contains(-literal)) {
        clauses.set(i, REMOVED);
        continue;
      }
      for (int k = start; ; ++k) {
        if (i == k) {
          continue;
        }
        final int other = clauses.get(k);
        if (other == 0) {
          break;
        }
        if (other == REMOVED) {
          continue;
        }
        TIntHashSet neighbours = dag.neighbours(-literal);
        if (neighbours == null) {
          if (literal == -other) {
            // literal + -literal = true
            return true;
          }
          if (literal == other) {
            // literal + literal = literal
            clauses.set(k, REMOVED);
            continue;
          }
        } else {
          if (neighbours.contains(other)) {
            // if literal + other ... and -literal => other
            // then true
            return true;
          }
          if (neighbours.contains(-other)) {
            // if literal + other + ... and -literal => -other
            // then literal + ...
            clauses.set(k, REMOVED);
            continue;
          }
        }
      }
    }
  }

  /**
   * Hyper-binary resolution.
   *
   * @return clauses representing binaries and units discovered.
   */
  public TIntArrayList hyperBinaryResolution() {
    logger.debug("Running hyperBinaryResolution()");
    int numUnits = 0, numBinaries = 0;
    int numLiterals = 0, clauseSum = 0;
    TIntIntHashMap counts = new TIntIntHashMap();
    TIntIntHashMap sums = new TIntIntHashMap();

    TIntArrayList newClauses = new TIntArrayList();
    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.getQuick(i);
      if (literal == 0) {
        for (TIntIntIterator it = counts.iterator(); it.hasNext(); ) {
          it.advance();
          if (it.value() == numLiterals && !units.contains(-it.key())) {
            pushClause(newClauses, -it.key());
            ++numUnits;
          } else if (it.value() == numLiterals - 1) {
            int missingLiteral = clauseSum - sums.get(it.key());
            if (!dag.containsEdge(it.key(), missingLiteral)) {
              pushClause(newClauses, -it.key(), missingLiteral);
              ++numBinaries;
            }
          }
        }
        numLiterals = 0;
        clauseSum = 0;
        counts.clear();
        sums.clear();
      } else {
        ++numLiterals;
        clauseSum += literal;
        TIntHashSet neighbours = dag.neighbours(literal);
        if (neighbours != null) {
          for (TIntIterator it = neighbours.iterator(); it.hasNext(); ) {
            int node = -it.next();
            counts.adjustOrPutValue(node, 1, 1);
            sums.adjustOrPutValue(node, literal, literal);
          }
        }
      }
    }

    logger.debug("Hyper binary resolution found " + numUnits + " unit(s) and "
                 + numBinaries + " binary(ies)");
    return newClauses;
  }

  /**
   * Pure literal assignment.
   *
   * @return clauses representing units discovered.
   */
  public TIntArrayList pureLiterals() {
    logger.debug("Running pureLiterals()");
    BitSet bs = new BitSet();
    for (int i = 0; i < clauses.size(); ++i) {
      bs.set(getRecursiveProxy(clauses.get(i)));
    }
    for (int u : dag.nodes()) {
      if (dag.neighbours(u).size() > 1) {
        bs.set(-u);
      }
    }
    for (int u : units.toArray()) {
      bs.set(u);
    }

    // 0 is in bs, but -0 is also so
    // 0 will not be considered a pure literal.
    TIntArrayList pureLiterals = new TIntArrayList();
    int numUnits = 0;
    for (int literal : bs.elements()) {
      if (!bs.get(-literal) && !units.contains(literal)) {
        pushClause(pureLiterals, literal);
        ++numUnits;
      }
    }

    logger.debug("Discovered " + numUnits + " pure literal(s)");
    return pureLiterals;
  }

  /**
   * Recursively finds the proxy of u.
   * The returned value doesn't have any proxy.
   */
  private int getRecursiveProxy(final int u) {
    if (proxies.contains(u)) {
      int v = getRecursiveProxy(proxies.get(u));
      proxies.put(u, v);
      return v;
    }
    return u;
  }
}
