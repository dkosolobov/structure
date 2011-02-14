package ibis.structure;

import java.util.concurrent.Semaphore;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
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
  private static final int REMOVED = Integer.MAX_VALUE;
  private static final TIntHashSet EMPTY = new TIntHashSet();

  private static final Logger logger = Logger.getLogger(Solver.class);

  /** Pool of executors for parallelization. */
  private static  ExecutorService pool = null;

  /** Number of variables. */
  private int numVariables;
  /** Set of true literals discovered. */
  private BitSet units = new BitSet();
  /** Equalities between literals. */
  private int[] proxies;
  /** The implication graph. */
  private ImplicationsGraph graph;
  /** List of clauses separated by 0. */
  private TIntArrayList clauses;
  /** Watchlists. */
  private TIntHashSet[] watchLists;
  /** Maps start of clause to # unsatisfied literals. */
  private TIntIntHashMap lengths = new TIntIntHashMap();
  /** Queue of clauses with at most 2 unsatisfied literals. */
  private TIntArrayList queue = new TIntArrayList();
  /** Queue of units to be propagated. */
  private TIntArrayList unitsQueue = new TIntArrayList();

  /** Creates the pool of executors */
  public static void createThreadPool() {
    pool = Executors.newFixedThreadPool(Configure.numExecutors);
  }

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
  private TIntHashSet watchList(final int u) {
    return watchLists[u + numVariables];
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
        pureLiterals();
        propagate();
      }
    }

    renameEquivalentLiterals();
    graph.transitiveClosure();
    propagate();

    for (int i = 0; i < Configure.numHyperBinaryResolutions; ++i) {
      if (!hyperBinaryResolution()) {
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

    renameEquivalentLiterals();
    missingLiterals();
    verify();
  }

  /**
   * Propagates one clause.
   *
   * @param start position of first literal in the clause to be propagated
   * @throws ContradictionException if clause has length 0
   */
  private void propagateClause(final int start) throws ContradictionException {
    if (isSatisfied(start)) {
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
        assert isSatisfied(position);
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

  /**
   * Does hyper binary resolution.
   */
  private class HyperBinaryResolution implements Runnable {
    /** A semaphore to signal end of computation */
    private Semaphore semaphore;
    /** A vector to store generated binaries. */
    private TIntArrayList binaries;
    /** An iterator over clauses. */
    private TIntIntIterator iterator;

    public HyperBinaryResolution(final Semaphore semaphore,
                                 final TIntArrayList binaries,
                                 final TIntIntIterator iterator) {
      this.semaphore = semaphore;
      this.binaries = binaries;
      this.iterator = iterator;
    }

    public void run() {
      int[] counts = new int[2 * numVariables + 1];
      int[] sums = new int[2 * numVariables + 1];
      int[] touched = new int[2 * numVariables + 1];
      int[] twice = new int[2 * numVariables + 1];
      int twiceColor = 0;

      // Cache is used to filter many duplicate binaries.
      final int cacheSize = 1 << 10;
      int[] cache = new int[cacheSize * 2];
      int limit = (int) Math.pow(numVariables, 1.5);

      while (true) {
        int start;
        synchronized (iterator) {
          if (!iterator.hasNext()) break;
          iterator.advance();
          start = iterator.key();
        }
        
        int numLiterals = 0;
        int clauseSum = 0;
        int numTouched = 0;
        int end = start;

        for (; end < clauses.size(); end++) {
          int literal = clauses.get(end);
          if (literal == REMOVED) {
            continue;
          }
          if (literal == 0) {
            break;
          }

          twiceColor++;
          numLiterals++;
          clauseSum += literal;
          TIntArrayList edges = graph.edges(literal);
          for (int i = 0; i < edges.size(); i++) {
            int node = -edges.get(i) + numVariables;
            if (twice[node] != twiceColor) {
              twice[node] = twiceColor;
              if (counts[node] == 0) touched[numTouched++] = node;
              counts[node] += 1;
              sums[node] += literal;
            }
          }
        }

        start = end + 1;

        for (int i = 0; i < numTouched; ++i) {
          int touch = touched[i];
          int literal = touch - numVariables;
          assert !isLiteralAssigned(literal);

          if (counts[touch] == numLiterals) {
            synchronized (unitsQueue) {
              unitsQueue.add(-literal);
            }
          } else if (counts[touch] + 1 == numLiterals) {
            // There is an edge from literal to all literals in clause except one.
            // New implication: literal -> missing
            int missing = clauseSum - sums[touch];
            assert !isLiteralAssigned(missing);

            if (literal != missing) {
              int hash = hash(missing) ^ literal;
              hash = 2 * (hash & (cacheSize - 1));
              if (cache[hash] != -literal || cache[hash + 1] != missing) {
                cache[hash] = -literal;
                cache[hash + 1] = missing;
                synchronized (binaries) {
                  binaries.add(-literal);
                  binaries.add(missing);
                  if (binaries.size() > limit) {
                    break;
                  }
                }
              }
            }
          }

          counts[touch] = 0;
          sums[touch] = 0;
        }
      }

      semaphore.release(1);
    }
  }

  /**
   * Performes hyper-binary resolution.
   *
   * If (a1 + ... ak + b) and (l &ge; -a1) ... (l &ge; -ak)
   * then l &ge; b, otherwise if l then clause is contradiction
   *
   * @return true if any unit or binary was discovered
   * @throws ContradictionException if contradiction was found
   */
  public boolean hyperBinaryResolution() throws ContradictionException {
    Semaphore semaphore = new Semaphore(0);
    TIntArrayList binaries = new TIntArrayList();
    TIntIntIterator iterator = lengths.iterator();

    // Runs numThreads HyperBinaryResolution simultaneously.
    int numThreads = Math.min(Configure.numExecutors,
                              Math.max(1, lengths.size() / 10000));
    for (int i = 1; i < numThreads; i++) {
      pool.execute(new HyperBinaryResolution(semaphore, binaries, iterator));
    }
    HyperBinaryResolution local = new HyperBinaryResolution(semaphore, binaries, iterator);
    local.run();
    try {
      semaphore.acquire(numThreads);
    } catch (InterruptedException e) {
      // ignored
    }

    // Filters some duplicate binaries
    int[] last = new int[2 * numVariables + 1];
    for (int i = 0; i < binaries.size(); i += 2) {
      int u = binaries.get(i), u_ = u + numVariables;
      int v = binaries.get(i + 1), v_ = v + numVariables;
      if (last[u_] != v && last[v_] != u) {
        // Binary was not found in cached
        last[u_] = v;
        addBinary(u, v);
      }
    }

    int numUnits = unitsQueue.size();
    int numBinaries = binaries.size() / 2;
    if (Configure.verbose) {
      if (numUnits > 0) {
        System.err.print("hu" + numUnits + ".");
      }
      if (numBinaries > 0) {
        System.err.print("hb" + numBinaries + ".");
      }
    }
    return numUnits > 0 || numBinaries > 0;
  }

  /**
   * Assigns literals that appear only as plus or as minus.
   *
   * @return true if any unit was discovered
   * @throws ContradictionException if contradiction was found
   */
  public boolean pureLiterals() throws ContradictionException {
    for (int u = 1; u <= numVariables; u++) {
      if (!isLiteralAssigned(u)) {
        if (numBinaries(u) == 0 && watchList(u).size() == 0) {
          unitsQueue.add(-u);
          continue;
        }
        if (numBinaries(-u) == 0 && watchList(-u).size() == 0) {
          unitsQueue.add(u);
          continue;
        }
      }
    }

    int numUnits = unitsQueue.size();
    if (Configure.verbose) {
      if (numUnits > 0) {
        System.err.print("p" + numUnits + ".");
      }
    }
    return numUnits > 0;
  }

  /**
   * Assigns literals don't appear at all in the instance.
   */
  public boolean missingLiterals() {
    int numUnits = 0;
    for (int u = 1; u <= numVariables; u++) {
      if (!isLiteralAssigned(u)) {
        if (numBinaries(u) == 0 && watchList(u).size() == 0) {
          if (numBinaries(-u) == 0 && watchList(-u).size() == 0) {
            units.add(u);
            numUnits++;
            continue;
          }
        }
      }
    }

    if (Configure.verbose) {
      if (numUnits > 0) {
        System.err.print("m" + numUnits + ".");
      }
    }
    return numUnits > 0;
  }

  /**
   * Performs binary (self) subsumming.
   *
   * @return true if any literal was removed.
   */
  public boolean binarySelfSubsumming() {
    int numSatisfiedClauses = 0, numRemovedLiterals = 0;
    TouchSet touched = new TouchSet(numVariables);

    for (int start : lengths.keys()) {
      // Finds the end of the clause
      int end = start - 1;
      while (clauses.get(++end) != 0) { }

    search:
      for (int i = start; i < end; ++i) {
        int first = clauses.get(i);
        if (first == REMOVED) {
          continue;
        }

        TIntArrayList edges = graph.edges(-first);
        touched.reset();
        for (int j = 0; j < edges.size(); j++) {
          touched.add(edges.get(j));
        }

        for (int j = start; j < end; ++j) {
          int second = clauses.get(j);
          if (i == j || second == REMOVED) {
            continue;
          }

          if (touched.contains(-second)) {
            // If a + b + c + ... and -a => -b
            // then a + c + ...
            removeLiteral(start, second, j);
            ++numRemovedLiterals;
            continue;
          }

          if (touched.contains(second)) {
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

    if (Configure.verbose) {
      if (numRemovedLiterals > 0) {
        System.err.print("bl" + numRemovedLiterals + ".");
      }
      if (numSatisfiedClauses > 0) {
        System.err.print("bc" + numSatisfiedClauses + ".");
      }
    }
    return numRemovedLiterals > 0 || numSatisfiedClauses > 0;
  }

  /**
   * Returns a hash of a.
   * This function is better than the one from
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
   * Performs clause subsumming.
   *
   * Removes clauses that include other clauses.
   * This is the algorithm implemented in SatELite.
   */
  public void subsumming() {
    long startTime = System.currentTimeMillis();

    int[] keys = lengths.keys();
    long[] hashes = new long[keys.length];
    TIntIntHashMap index = new TIntIntHashMap();

    // Computes clauses' hashes
    for (int i = 0; i < keys.length; ++i) {
      int start = keys[i];
      index.put(start, i);

      long hash = 0;
      int end = start;
      for (;; end++) {
        int u = clauses.get(end);
        if (u == REMOVED) {
          continue;
        }
        if (u == 0) {
          break;
        }
        hash |= 1L << (hash(u) >>> 26);
      }
      // To fast check that A is not included in B
      // ~hashes[A] & hashes[B] != 0
      hashes[i] = ~hash;
    }

    // List of subsummed clauses to be removed.
    TIntHashSet subsummed = new TIntHashSet();
    // Same as watchList() but as an array for faster access.
    int[][] watchLists = new int[2 * numVariables + 1][];

    // For each clause finds which other clause includes it.
    int numTotal = 0, numTries = 0, numGood = 0;
    for (int start : keys) {
      if (subsummed.contains(start)) {
        continue;
      }

      int startIndex = index.get(start);
      final long startHash = ~hashes[startIndex];

      // Finds literal included in minimum number of clauses
      int bestLiteral = 0;
      int minNumClauses = Integer.MAX_VALUE;
      int end = start;
      for (;; end++) {
        int literal = clauses.get(end);
        if (literal == REMOVED) {
          continue;
        }
        if (literal == 0) {
          break;
        }
        int numClauses = watchList(literal).size();
        if (numClauses < minNumClauses) {
          bestLiteral = literal;
          minNumClauses = numClauses;
        }
      }

      if (end - start > 16) {
        // Ignores very long clauses to improve perfomance.
        continue;
      }
      if (watchList(bestLiteral).size() <= 1) {
        // Can't include any other clause.
        continue;
      }

      // Lazy initializes the watchList
      int[] watchList = watchLists[bestLiteral + numVariables];
      if (watchList == null) {
        watchList = watchList(bestLiteral).toArray();
        watchLists[bestLiteral + numVariables] = watchList;
        for (int i = 0; i < watchList.length; i++) {
          watchList[i] = index.get(watchList[i]);
        }
      }

      numTotal += watchList.length - 1;
      for (int clause : watchList) {
        if ((startHash & hashes[clause]) != 0) {
          // Fast inclusion testing failed.
          continue;
        }
        if (clause == startIndex) {
          // Ignores identical clause.
          continue;
        }

        numTries++;
        clause = keys[clause];

        boolean isSubsummed = true;
        for (int j = start; isSubsummed; j++) {
          int literal = clauses.get(j);
          if (literal == REMOVED) {
            continue;
          }
          if (literal == 0) {
            break;
          }
          isSubsummed = watchList(literal).contains(clause);
        }

        if (isSubsummed) {
          numGood++;
          subsummed.add(clause);
        }
      }
    }

    int numRemovedClauses = subsummed.size();
    TIntIterator it = subsummed.iterator();
    for (int size = subsummed.size(); size > 0; size--) {
      int clause = it.next();
      if (!isSatisfied(clause)) {
        removeClause(clause);
      }
    }

    if (Configure.verbose) {
      if (numRemovedClauses > 0) {
        System.err.print("ss" + numRemovedClauses + ".");
      }
    }

    long endTime = System.currentTimeMillis();
    // logger.info("ss took " + (endTime - startTime) / 1000.);
    // logger.info("numTotal = " + numTotal + "; numTries = " + numTries + "; numGood = " + numGood);
    // System.exit(1);
  }


  /** Returns true if literal u is already assigned. */
  private boolean isLiteralAssigned(final int u) {
    assert u != 0;
    return getRecursiveProxy(u) != u || units.get(u) || units.get(-u);
  }

  /**
   * Returns true if clause was satisfied.
   * NOTE: satisfied clauses are removed from lengths.
   *
   * @param clause start of clause
   * @return true if clause was satisfied.
   */
  private boolean isSatisfied(final int clause) {
    return !lengths.contains(clause);
  }

  /**
   * Returns number of binaries (in DAG) containing literal.
   *
   * @param u a literal
   * @return number of binaries containing literal
   */
  private int numBinaries(final int u) {
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
  private void removeLiteral(final int clause, final int u, final int position) {
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
  private void removeClause(final int clause) {
    lengths.remove(clause);
    assert isSatisfied(clause);
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
  private void addBinary(final int u, final int v) {
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
