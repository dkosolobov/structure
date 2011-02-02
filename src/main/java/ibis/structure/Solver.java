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
  private static final int REMOVED = Integer.MAX_VALUE;

  private static final Logger logger = Logger.getLogger(Solver.class);
  private static final java.util.Random random = new java.util.Random(1);

  /** Number of variables. */
  private int numVariables;
  /** Set of true literals discovered. */
  private BitSet units = new BitSet();
  /** Equalities between literals. */
  private int[] proxies;
  /** The implication graph. */
  private DAG dag;
  /** List of clauses separated by 0. */
  private TIntArrayList clauses;
  /** Watchlists. */
  private TIntHashSet[] watchLists;
  /** Maps start of clause to # unsatisfied literals. */
  private TIntIntHashMap lengths = new TIntIntHashMap();
  /**
   * Queue of clauses with at most 2 unsatisfied literals.
   * May contains satisfied clauses.
   */
  private TIntArrayList queue = new TIntArrayList();

  /**
   * Constructor.
   *
   * @param instance instance to solve
   */
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

    if (Configure.verbose) {
      System.err.print(".");
      System.err.flush();
    }
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
        if (satisfied) {
          removeClause(start);
        } else if (length <= 2) {
          queue.add(start);
        }
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

  /**
   * Verifies that assigned instances are removed.
   */
  private void verifyAssigned() {
    for (int u = -numVariables; u <= numVariables; ++u) {
      if (u != 0 && isLiteralAssigned(u)) {
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
    skeleton.append(dag.skeleton());
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
  public DAG dag() {
    return dag;
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
   * Returns a normalized literal to branch on.
   *
   * @param branch literal to branch on. 0 for no branching.
   * @return solution or simplified instance.
   */
  public Solution solve(final int branch) {
    buildWatchLists();
    if (branch != 0) {
      addUnit(branch);
    }

    try {
      simplify();
    } catch (ContradictionException e) {
      return Solution.unsatisfiable();
    }

    // Checks if all clauses were satisfied.
    boolean satisfied = true;
    for (int literal = -numVariables; literal <= numVariables; literal++) {
      if (literal != 0) {
        if (!watchList(literal).isEmpty()) {
          satisfied = false;
          break;
        }
      }
    }

    if (satisfied) {
      // Solves the remaining 2SAT encoded in the implication graph
      for (TIntIterator it = dag.solve().iterator(); it.hasNext();) {
        addUnit(it.next());
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
    core.append(dag.skeleton());
    core.append(compact());
    return new Core(units.elements(), proxies, core);
  }

  /** Computes a score for a possible branch.  */
  private double score(final int branch) {
    int num = 0;
    TIntHashSet neighbours = dag.neighbours(branch);
    if (neighbours != null) {
      TIntIterator it = neighbours.iterator();
      for (int size = neighbours.size(); size > 0; size--) {
        int literal = it.next();
        num += watchList(literal).size();
      }
      return Math.pow(2 + random.nextDouble(), num);
    }
    num = watchList(branch).size();
    return Math.pow(1 + random.nextDouble(), num);
  }

  /** Returns a literal for branching. */
  public int chooseBranchingLiteral() {
    int bestBranch = 0;
    double bestValue = Double.NEGATIVE_INFINITY;
    for (int branch = 1; branch <= numVariables; ++branch) {
      boolean isMutex =
          watchList(branch).isEmpty() && watchList(-branch).isEmpty();
      if (!isMutex && !isLiteralAssigned(branch)) {
        double positive = score(branch);
        double negative = score(-branch);
        double value = positive * negative;
        if (value > bestValue) {
          bestBranch = positive > negative ? -branch : branch;
          bestValue = value;
        }
      }
    }

    assert bestBranch != 0;
    return bestBranch;
  }

  /**
   * Simplifies the instance.
   *
   * @param isRoot true if solver is at the root of branching tree
   * @throws ContradictionException if contradiction was found
   */
  public void simplify() throws ContradictionException {
    propagate();

    for (int i = 0; i < Configure.numHyperBinaryResolutions; ++i) {
      if (!hyperBinaryResolution()) {
        break;
      }
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

    pureLiterals();
    propagate();

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
        if (!isSatisfied(start)) {
          removeClause(start);
        }
        break;

      default:
        assert false : "Clause must have 0, 1 or 2 literals, not " + length;
    }
  }

  /**
   * Propagates all clauses in queue.
   *
   * @return true if any any clause was propagated.
   * @throws ContradictionException if a clause of length 0 was found
   */
  private boolean propagate() throws ContradictionException {
    // NOTE: any new clause is appended.
    for (int i = 0; i < queue.size(); ++i) {
      propagateClause(queue.get(i));
    }

    boolean simplified = !queue.isEmpty();
    queue.clear();
    return simplified;
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
        if (literal == 0) {
          break;
        }
        if (literal == REMOVED) {
          continue;
        }

        numLiterals++;
        clauseSum += literal;
        TIntHashSet neighbours = dag.neighbours(literal);
        if (neighbours != null) {
          TIntIterator it = neighbours.iterator();
          for (int size = neighbours.size(); size > 0; --size) {
            int node = mapZtoN(-it.next());
            if (counts[node] == 0) {
              touched[numTouched++] = node;
            }
            counts[node] += 1;
            sums[node] += literal;
          }
        }
      }

      if (numLiterals < 3) {
        // Clause is too small for hyper binary resolution.
        // propagateClause(start);
        continue;
      }

      for (int i = 0; i < numTouched && !isSatisfied(start); ++i) {
        int touch = touched[i];
        int literal = mapNtoZ(touch);

        if (counts[touch] == numLiterals) {
          // There is an edge from literal to all literals in clause.
          int proxy = getRecursiveProxy(literal);
          if (units.contains(proxy)) {
            throw new ContradictionException();
          }
          if (!units.contains(-proxy)) {
            addUnit(-proxy);
            ++numUnits;
          }
        } else if (counts[touch] + 1 == numLiterals) {
          // There is an edge from literal to all literals in clause except one.
          // New implication: proxy -> missing
          int proxy = getRecursiveProxy(literal);
          int missing = getRecursiveProxy(clauseSum - sums[touch]);

          if (units.contains(proxy)) {
            if (units.contains(-missing)) {
              // true -> false
              throw new ContradictionException();
            } else if (!units.contains(missing)) {
              // true -> missing => missing
              addUnit(missing);
              ++numUnits;
            }
          } else if (units.contains(-proxy) || units.contains(missing)) {
            // false -> missing or proxy -> true
          } else if (units.contains(-missing)) {
            // proxy -> false => -proxy
            addUnit(-proxy);
            ++numUnits;
          } else {
            if (!dag.containsEdge(proxy, missing)) {
              addBinary(-proxy, missing);
              ++numBinaries;
            }
          }
        }

        counts[touch] = 0;
        sums[touch] = 0;
      }
    }

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
   * Pure literal assignment.
   *
   * @return true if any unit was discovered
   * @throws ContradictionException if contradiction was found
   */
  public boolean pureLiterals() throws ContradictionException {
    int numUnits = 0;

    for (int u = 1; u <= numVariables; u++) {
      if (isLiteralAssigned(u)) {
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

    if (Configure.verbose) {
      if (numUnits > 0) {
        System.err.print("p" + numUnits + ".");
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
    int[] keys = lengths.keys();

    // Computes clause hashes
    TIntLongHashMap hashes = new TIntLongHashMap();
    for (int i = 0; i < keys.length; ++i) {
      int start = keys[i];
      long hash = 0;
      for (int j = start;; j++) {
        int literal = clauses.get(j);
        if (literal == 0) {
          break;
        }
        if (literal == REMOVED) {
          continue;
        }
        hash |= 1 << (hash(literal) >>> 26);
      }
      hashes.put(start, hash);
    }

    // For each clause finds which other clause includes it.
    int numTries = 0, numGood = 0;
    int numRemovedClauses = 0;
    for (int i = 0; i < keys.length; ++i) {
      int start = keys[i];
      if (isSatisfied(start)) {
        // Ignores already subsummed clauses.
        continue;
      }
      if (lengths.get(start) > 16) {
        // Ignores very long clauses to improve perfomance.
        continue;
      }


      // Finds literal included in minimum number of clauses
      int bestLiteral = 0;
      int minNumClauses = Integer.MAX_VALUE;
      for (int j = start;; j++) {
        int literal = clauses.get(j);
        if (literal == 0) {
          break;
        }
        if (literal == REMOVED) {
          continue;
        }
        int numClauses = watchList(literal).size();
        if (numClauses < minNumClauses) {
          bestLiteral = literal;
          minNumClauses = numClauses;
        }
      }

      if (watchList(bestLiteral).size() <= 1) {
        // Can't include any other clause.
        continue;
      }
      final long startHash = hashes.get(start);
      for (int clause : watchList(bestLiteral).toArray()) {
        if (clause == start) {
          // Ignores identical clause
          continue;
        }
        if ((startHash & hashes.get(clause)) != startHash) {
          // Fast inclusion testing.
          continue;
        }

        boolean good = true;
        for (int j = start; good; j++) {
          int literal = clauses.get(j);
          if (literal == 0) {
            break;
          }
          if (literal == REMOVED) {
            continue;
          }
          good = watchList(literal).contains(clause);
        }

        if (good) {
          ++numRemovedClauses;
          removeClause(clause);
        }
      }
    }

    if (Configure.verbose) {
      if (numRemovedClauses > 0) {
        System.err.print("ss" + numRemovedClauses + ".");
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
    return watchLists[mapZtoN(u)];
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
    TIntHashSet neighbours = dag.neighbours(-u);
    return neighbours == null ? 0 : neighbours.size() - 1;
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

  /**
   * Removes literal u from clause.
   *
   * @param clause start of clause
   * @param u literal to remove
   */
  private void removeLiteral(final int clause, final int u) {
    assert u != 0 : "Cannot remove literal 0 from clause";
    watchList(u).remove(clause);
    clauses.set(findLiteral(clause, u), REMOVED);
    assert lengths.contains(clause);
    int newLength = lengths.adjustOrPutValue(clause, -1, 0xA3A3A3A3);
    if (newLength <= 2) {
      queue.add(clause);
    }
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
      int literal = clauses.get(c);
      clauses.set(c, REMOVED);
      if (literal == 0) {
        break;
      }
      if (literal == REMOVED) {
        continue;
      }
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
    assert !isLiteralAssigned(u) : "Unit " + u + " already assigned";

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
   *
   * @param u a literal
   * @param v a literal
   */
  private void addBinary(final int u, final int v) {
    assert !isLiteralAssigned(u) : "First literal " + u + " is assigned";
    assert !isLiteralAssigned(v) : "Second literal " + v + " is assigned";

    // propagates contradictions contradictions
    TIntHashSet contradictions = dag.findContradictions(-u, v);
    if (!contradictions.isEmpty()) {
      int[] contradictions_ = contradictions.toArray();
      for (int unit : contradictions_) {
        assert !units.contains(-unit);
        if (!units.contains(unit)) {
          addUnit(unit);
        }
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
