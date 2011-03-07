package ibis.structure;

import java.util.Vector;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/**
 * Performs clause subsumming.
 *
 * Removes clauses that include other clauses.
 * This is the algorithm implemented in SatELite.
 */
public final class SelfSubsumming {
  private static final Logger logger = Logger.getLogger(SelfSubsumming.class);

  private final Solver solver;
  private final int numVariables;
  private final WatchLists watchLists;
  private final TIntArrayList formula;
  /** Maps clauses to their hash */
  private final TIntLongHashMap hashes = new TIntLongHashMap();
  /** Used to check if a clause is included in another clause */
  private final TouchSet visited;
  /** Number of removed clauses */
  private int numRemovedClauses = 0;

  /** Constructor */
  private SelfSubsumming(final Solver solver) {
    this.solver = solver;

    numVariables = solver.numVariables;
    watchLists = solver.watchLists;
    formula = watchLists.formula();
    visited = new TouchSet(numVariables);
  }

  public static boolean run(final Solver solver)
      throws ContradictionException {
    solver.verifyIntegrity();
    boolean simplified = (new SelfSubsumming(solver)).run();
    solver.verifyIntegrity();
    solver.propagate();
    solver.verifyIntegrity();
    return simplified;
  }

  /** @return true if any clause was removed. */
  private boolean run() throws ContradictionException {
    // prevIndex, last and clauses encode (very compact)
    // 2 * numVariables single linked lists (one for each variable)
    // containing clauses where literal is the best literal.
    TIntArrayList prevIndex = new TIntArrayList();
    int[] last = new int[2 * numVariables + 1];
    java.util.Arrays.fill(last, -1);

    // Computes clauses' hashes and bestLiterals.
    // Best literal of a clause is the literal in the clause
    // that appears in minimum number of clauses in the formula.
    TIntArrayList clauses = getClauses();
    for (int i = 0; i < clauses.size(); i++) {
      int clause = clauses.get(i);
      hashes.put(clause, clauseHash(clause));
      int bestLiteral = bestLiteral(clause);
      prevIndex.add(last[bestLiteral + numVariables]);
      last[bestLiteral + numVariables] = i;
    }

    // Clauses and their hashes containing current literal, u.
    // This is mainly an optimization to avoid TIntLongHashMap.get().
    TIntArrayList uClauses = new TIntArrayList();
    TLongArrayList uHashes = new TLongArrayList();
    TouchSet touched = new TouchSet(numVariables);
    for (int u = -numVariables; u <= numVariables; ++u) {
      if (last[u + numVariables] == -1) {
        continue;
      }

      uClauses.reset();
      uHashes.reset();
      TIntIterator it1 = watchLists.get(u).iterator();
      for (int size = watchLists.get(u).size(); size > 0; size--) {
        int clause = it1.next();
        uClauses.add(clause);
        uHashes.add(hashes.get(clause));
      }

      touched.reset();
      solver.graph.dfs(neg(u), touched);

      int index = last[u + numVariables];
      while (index != -1) {
        int clause = clauses.get(index);
        if (!isClauseRemoved(formula, clause)) {
          binarySelfSubsum(clause, u, touched);
          long hash = clauseHash(clause);
          hashes.put(clause, hash);
          findSubsummed(clause, uClauses, uHashes);
        }
        index = prevIndex.get(index);
      }
    }

    if (Configure.verbose) {
      if (numRemovedClauses > 0) {
        System.err.print("ss" + numRemovedClauses + ".");
      }
    }
    return numRemovedClauses > 0;
  }

  /**
   * If a + b + c + ... and -a &ge; -b
   * then a + c + ...
   */
  private void binarySelfSubsum(final int clause,
                                final int literal,
                                final TouchSet touched)
      throws ContradictionException {
    if (type(formula, clause) != OR) {
      return;
    }

    int length = length(formula, clause);
    for (int j = clause; j < clause + length; j++) {
      int u = formula.getQuick(j);
      if (u != literal && touched.contains(neg(u))) {
        watchLists.removeLiteralAt(clause, j);
        length--;
        j--;
      }
    }
  }

  /**
   * Removes clauses in uClauses subsummed by clause.
   */
  private void findSubsummed(final int clause,
                             final TIntArrayList uClauses,
                             final TLongArrayList uHashes)
      throws ContradictionException {
    long hash = hashes.get(clause);
    int length = length(formula, clause);
    int type = type(formula, clause);

    visited.reset();
    for (int j = clause; j < clause + length; j++) {
      visited.add(formula.getQuick(j));
    }

    for (int i = 0; i < uClauses.size(); i++) {
      int other = uClauses.getQuick(i);
      long otherHash = uHashes.getQuick(i);

      if ((hash & ~otherHash) != 0
          || isClauseRemoved(formula, other)
          || other == clause) {
        continue;
      }

      int otherLength = length(formula, other);
      int otherType = type(formula, other);

      int included = 0;
      for (int j = other; j < other + otherLength; j++) {
        if (visited.contains(formula.get(j))) {
          included++;
        }
      }

      if (included != length) {
        // Not all literals in clause are in other
        continue;
      }

      /*
      logger.info(clauseToString(formula, clause) + " subsumes "
          + clauseToString(formula, other));
          */

      if (type == OR && otherType == OR) {
        numRemovedClauses++;
        watchLists.removeClause(other);
      } else if (type != OR && otherType != OR) {
        if (type == XOR) {
          switchXOR(formula, other);
        }

        // TODO: this is can be somehow faster
        for (int j = clause; j < clause + length; j++) {
          int literal = formula.getQuick(j);
          watchLists.removeLiteral(other, literal);
        }

        // recomputes other's hash
        otherHash = clauseHash(clause);
        hashes.put(other, otherHash);
        uHashes.set(i, otherHash);
      }
    }
  }

  /**
   * Returns clauses sorted by decreasing length.
   */
  private TIntArrayList getClauses() {
    Vector<TIntArrayList> clauses = new Vector<TIntArrayList>();

    ClauseIterator it = new ClauseIterator(formula);
    int numClauses = 0;
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      if (length <= 16) {
        if (length >= clauses.size()) {
          clauses.setSize(length + 1);
        }
        if (clauses.get(length) == null) {
          clauses.set(length, new TIntArrayList());
        }
        clauses.get(length).add(clause);
        numClauses++;
      }
    }

    TIntArrayList all = new TIntArrayList(numClauses);
    for (int i = clauses.size() - 1; i >= 0; i--) {
      if (clauses.get(i) != null) {
        all.addAll(clauses.get(i));
      }
    }

    return all;
  }

  /**
   * To fast check that A is not included in B
   * hashes[A] &amp; ~hashes[B] != 0
   */
  private long clauseHash(final int clause) {
    long hash = 0;
    int length = length(formula, clause);
    for (int i = clause; i < clause + length; i++) {
      int h = Hash.hash(formula.getQuick(i));
      hash |= 1L << ((h >>> 26) & 63);
    }
    return hash;
  }

  /**
   * Returns the literal in clause that appears the least
   * in other clauses.
   */
  private int bestLiteral(final int clause) {
    int bestLiteral = 0;
    int minNumClauses = Integer.MAX_VALUE;
    int length = length(formula, clause);

    for (int i = clause; i < clause + length; i++) {
      int literal = formula.get(i);
      int numClauses = watchLists.get(literal).size();

      if (numClauses < minNumClauses) {
        bestLiteral = literal;
        minNumClauses = numClauses;
      }
    }

    return bestLiteral;
  }
}
