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
  private int numRemovedLiterals = 0;

  /** Constructor */
  private SelfSubsumming(final Solver solver) {
    this.solver = solver;

    numVariables = solver.numVariables;
    watchLists = solver.watchLists;
    formula = solver.formula;
    visited = new TouchSet(numVariables);
  }

  public static boolean run(final Solver solver)
      throws ContradictionException {
    return (new SelfSubsumming(solver)).run();
  }

  /** @return true if any clause was removed. */
  private boolean run() throws ContradictionException {
    // prevIndex, last and clauses encode (very compact)
    // numVariables single linked lists (one for each variable)
    // containing clauses where literal is the best literal.
    TIntArrayList prevIndex = new TIntArrayList();
    int[] last = new int[numVariables + 1];
    java.util.Arrays.fill(last, -1);

    // Computes clauses' hashes and bestVariables.
    // Best literal of a clause is the literal in the clause
    // that appears in minimum number of clauses in the formula.
    TIntArrayList clauses = getClauses();
    for (int i = 0; i < clauses.size(); i++) {
      int clause = clauses.getQuick(i);
      int bestVariable = bestVariable(clause);
      hashes.put(clause, clauseHash(clause));
      prevIndex.add(last[bestVariable]);
      last[bestVariable] = i;
    }

    // Clauses and their hashes containing current literal, u.
    // This is mainly an optimization to avoid TIntLongHashMap.get().
    TIntArrayList uClauses = new TIntArrayList();
    TLongArrayList uHashes = new TLongArrayList();
    TouchSet touched = new TouchSet(numVariables);
    for (int u = 1; u <= numVariables; ++u) {
      if (last[u] == -1) {  // Empty list
        continue;
      }

      uClauses.reset();
      uHashes.reset();
      TIntIterator it1;
      
      it1 = watchLists.get(u).iterator();
      for (int size = watchLists.get(u).size(); size > 0; size--) {
        int clause = it1.next();
        uClauses.add(clause);
        uHashes.add(hashes.get(clause));
      }
      it1 = watchLists.get(neg(u)).iterator();
      for (int size = watchLists.get(neg(u)).size(); size > 0; size--) {
        int clause = it1.next();
        uClauses.add(clause);
        uHashes.add(hashes.get(clause));
      }

      int index = last[u];
      while (index != -1) {
        int clause = clauses.get(index);
        if (!isClauseRemoved(formula, clause)) {
          long hash = clauseHash(clause);
          hashes.put(clause, hash);
          findSelfSubsummed(clause, uClauses, uHashes);
        }
        index = prevIndex.get(index);
      }
    }

    if (Configure.verbose) {
      if (numRemovedLiterals > 0) {
        System.err.print("ss" + numRemovedLiterals + ".");
      }
    }
    return numRemovedLiterals > 0;
  }

  /**
   * Removes clauses in uClauses subsummed by clause.
   */
  private void findSelfSubsummed(final int clause,
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

next_clause:
    for (int i = 0; i < uClauses.size(); i++) {
      int other = uClauses.getQuick(i);
      long otherHash = uHashes.getQuick(i);

      // Fast inclusion check.
      if ((hash & ~otherHash) != 0
          || isClauseRemoved(formula, other)
          || other == clause) {
        continue next_clause;
      }

      // At this point there is a high probability that
      // all variables of 'clause' are included in variables of 'other'.

      int otherLength = length(formula, other);
      int otherType = type(formula, other);

      int included = 0;
      int selfLiteral = 0;   // at most one variable can have a different sign

      for (int j = other; j < other + otherLength; j++) {
        int literal = formula.getQuick(j);
        if (visited.contains(literal)) {
          included++;
        } else if (visited.contains(neg(literal))) {
          if (selfLiteral != 0) {
            // Two literals with different signs
            continue next_clause;
          }
          assert literal != 0;
          selfLiteral = literal;
          included++;
        }
      }

      if (included != length) {
        // Not all literals in clause are in other
        continue next_clause;
      }

      boolean recompute = false;
      if (type == OR && otherType == OR) {
        if (selfLiteral == 0) {
          // clause subsumes other
          numRemovedLiterals += otherLength;
          watchLists.removeClause(other);
        } else {
          // clause selfsubsumes other at selfLiteral
          recompute = true;
          numRemovedLiterals++;
          watchLists.removeLiteral(other, selfLiteral);
        }
      } else if (type != OR && otherType != OR) {
        // clause subsumes other
        assert selfLiteral == 0;
        recompute = true;
        numRemovedLiterals += length;

        if (type == XOR) {
          switchXOR(formula, other);
        }

        // TODO: this is can be somehow faster
        for (int j = clause; j < clause + length; j++) {
          int literal = formula.getQuick(j);
          watchLists.removeLiteral(other, literal);
        }
      }
      
      if (recompute) {
        // recomputes other's hash
        otherHash = clauseHash(clause);
        hashes.put(other, otherHash);
        uHashes.set(i, otherHash);
      }
    }
  }

  /**
   * Returns clauses sorted by decreasing length.
   *
   * Performs a radix sort.
   */
  private TIntArrayList getClauses() {
    Vector<TIntArrayList> clauses = new Vector<TIntArrayList>(4);

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
      int h = Hash.hash(var(formula.getQuick(i)));
      hash |= 1L << ((h >>> 26) & 63);
    }
    return hash;
  }

  /**
   * Returns the literal in clause that appears the least
   * in other clauses.
   */
  private int bestVariable(final int clause) {
    int bestVariable = 0;
    int minNumClauses = Integer.MAX_VALUE;
    int length = length(formula, clause);

    for (int i = clause; i < clause + length; i++) {
      int literal = formula.get(i);
      int numClauses = watchLists.get(literal).size()
                       + watchLists.get(neg(literal)).size();

      if (numClauses < minNumClauses) {
        bestVariable = literal;
        minNumClauses = numClauses;
      }
    }

    return var(bestVariable);
  }
}
