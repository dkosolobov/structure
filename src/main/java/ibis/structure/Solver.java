package ibis.structure;

import java.text.DecimalFormat;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;;
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
  private static final int BACKTRACK_THRESHOLD = 1 << 8;
  private static final int BACKTRACK_MAX_CALLS = 1 << 12;

  public static double[] WEIGHTS = {
      15.843, 28.753, 26.465, 26.854, 25.294, -0.985
    };

  private static class ClauseIterator {
    private final Solver solver;
    private int oldStart, oldEnd;
    private int newStart, newEnd;
    private boolean simplified = false;

    public ClauseIterator(Solver solver_) {
      logger.debug("Running ClauseIterator()");
      solver = solver_;
      oldEnd = -1;
      newEnd = 0;
    }

    public int start() {
      return newStart;
    }

    public int end() {
      return newEnd - 1;
    }

    public boolean simplified() {
      return simplified;
    }

    public boolean next() throws ContradictionException {
      final TIntArrayList clauses = solver.clauses;

      while (true) {
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

        if (solver.cleanClause(oldStart, oldEnd)) {
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
        }
        if (length == 1) {
          solver.addUnit(clauses.get(newStart));
        }
        if (length == 2) {
          solver.addBinary(clauses.get(newStart), clauses.get(newStart + 1));
        } 

        return true;
      }
    }
  }

  private static java.util.Random random = new java.util.Random(1);

  // The set of true literals discovered.
  private TIntHashSet units;
  // The implication graph.
  private DAG dag = new DAG();
  // Stores equalities between literals.
  private TIntIntHashMap proxies = new TIntIntHashMap();
  // List of clauses separated by 0.
  private TIntArrayList clauses;
  private int backtrackCalls = 0;

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
   * Returns the current DAG.
   */
  public DAG dag() {
    return dag;
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


  /**
   * Returns number of neighbours in DAG for node
   * or 0 if node is not in DAG.
   */
  private int numBinaries(int node) {
    TIntHashSet neighbours = dag.neighbours(-node);
    return neighbours == null ? 0 : neighbours.size();
  }

  private static int numLookaheads = 0;
  private static int numHypers = 0;

  static {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        logger.info("Number of lookaheads is " + numLookaheads);
        logger.info("Number of hypers is " + numHypers);

        try {
          java.io.File file = new java.io.File("lookaheads");
          java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
          java.io.PrintStream dos = new java.io.PrintStream(fos);
          dos.println(numLookaheads);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }


  
  
  public int lookahead() throws ContradictionException {
    synchronized (Job.class) {
      ++numLookaheads;
    }
 
    simplify();
    if (clauses.size() <= BACKTRACK_THRESHOLD && backtrack()) {
      return 0;
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

    return bestLiteral;
  }


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
    // pureLiterals();
    while (hyperBinaryResolution());
    subSumming();
    while (pureLiterals());

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
      logger.debug(clauses.size() + " literals left (excluding binaries "
                   + "and units)");
    }
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
    ClauseIterator it = new ClauseIterator(this);
    while (it.next());
    return it.simplified();
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
  private boolean cleanClause(final int start, final int end) {
    // Renames literals to component.
    for (int i = start; i < end; ++i) {
      int literal = clauses.get(i);
      if (literal != REMOVED) {
        clauses.set(i, getRecursiveProxy(literal));
      }
    }

    // Checks if the clause is satisfied, removes unsatisfied
    // literals and does binary resolution.
    for (int i = start; i < end; ++i) {
      int literal = clauses.get(i);
      if (literal == REMOVED) {
        continue;
      }
      if (units.contains(literal)) {
        return true;
      }
      if (units.contains(-literal)) {
        clauses.setQuick(i, REMOVED);
        continue;
      }

      for (int k = start; k < end; ++k) {
        if (i == k) {
          continue;
        }
        int other = clauses.get(k);
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
            clauses.setQuick(k, REMOVED);
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
            clauses.setQuick(k, REMOVED);
            continue;
          }
        }
      }
    }
    return false;
  }

  /**
   * Hyper-binary resolution.
   *
   * @return true if instances was simplified
   */
  public boolean hyperBinaryResolution() throws ContradictionException {
    ++numHypers;
    logger.debug("Running hyperBinaryResolution()");
    ClauseIterator cit = new ClauseIterator(this);

    int numUnits = 0, numBinaries = 0;
    TIntIntHashMap counts = new TIntIntHashMap();
    TIntIntHashMap sums = new TIntIntHashMap();

    while (cit.next()) {
      int start = cit.start(), end = cit.end();
      int numLiterals = end - start;
      int clauseSum = 0;
      counts.clear();
      sums.clear();

      for (int i = start; i < end; ++i) {
        int literal = clauses.get(i);
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

      for (TIntIntIterator it = counts.iterator(); it.hasNext(); ) {
        it.advance();
        int literal = it.key();
        int count = it.value();
        if (count == end - start) {
          if (!units.contains(-literal)) {
            addUnit(-literal);
            ++numUnits;
          }
        } else if (count + 1 == end - start) {
          int other = clauseSum - sums.get(literal);
          if (!dag.containsEdge(literal, other)) {
            addBinary(-literal, other);
            ++numBinaries;
          }
        }
      }
    }

    logger.debug("Hyper binary resolution found " + numUnits + " unit(s) and "
                 + numBinaries + " binary(ies)");
    return numUnits > 0 || numBinaries > 0 || cit.simplified();
  }

  /**
   * Pure literal assignment.
   *
   * @return true if instances was simplified
   */
  public boolean pureLiterals() throws ContradictionException {
    logger.debug("Running pureLiterals()");
    BitSet bs = new BitSet();

    ClauseIterator cit = new ClauseIterator(this);
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

    logger.debug("Discovered " + numUnits + " pure literal(s)");
    return numUnits > 0 || cit.simplified();
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
