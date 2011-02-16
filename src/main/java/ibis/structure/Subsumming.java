package ibis.structure;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntLongHashMap;
import gnu.trove.TIntIterator;
import gnu.trove.TIntArrayList;
import gnu.trove.TLongArrayList;

import static ibis.structure.Misc.*;


/**
 * Performs clause subsumming.
 *
 * Removes clauses that include other clauses.
 * This is the algorithm implemented in SatELite.
 */
public final class Subsumming {
  /** @return true if any clause was removed. */
  public static boolean run(Solver solver) throws ContradictionException {
    final TIntArrayList formula = solver.watchLists.formula;

    TIntLongHashMap hashes = new TIntLongHashMap();
    ClauseIterator it;

    // Computes clauses' hashes
    it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      if (type(formula, clause) != OR) {
        continue;
      }

      long hash = 0;
      int length = length(formula, clause);
      for (int i = clause; i < clause + length; i++) {
        hash |= 1L << (Hash.hash(formula.get(i)) >>> 26);
      }

      // To fast check that A is not included in B
      // ~hashes[A] & hashes[B] != 0
      hashes.put(clause, ~hash);
    }

    // List of subsummed clauses to be removed.
    TIntHashSet subsummed = new TIntHashSet();
    // Same as watchList() but as an array for faster access.
    int[][] watchLists = new int[2 * solver.numVariables + 1][];

    // For each clause finds which other clause includes it.
    it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      if (type(formula, clause) != OR || subsummed.contains(clause)) {
        continue;
      }

      int length = length(formula, clause);
      long clauseHash = ~hashes.get(clause);

      if (length > 16) {
        // Ignores very long clauses to improve perfomance.
        continue;
      }

      // Finds literal included in minimum number of clauses
      int bestLiteral = 0;
      int minNumClauses = Integer.MAX_VALUE;
      for (int i = clause; i < clause + length; i++) {
        int literal = formula.get(i);
        int numClauses = solver.watchLists.get(literal).size();
        if (numClauses < minNumClauses) {
          bestLiteral = literal;
          minNumClauses = numClauses;
        }
      }

      if (solver.watchLists.get(bestLiteral).size() <= 1) {
        // Can't include any other clause.
        continue;
      }

      // Lazy initializes the watchList
      int[] watchList = watchLists[bestLiteral + solver.numVariables];
      if (watchList == null) {
        watchList = solver.watchLists.get(bestLiteral).toArray();
        watchLists[bestLiteral + solver.numVariables] = watchList;
      }

      for (int other : watchList) {
        if ((clauseHash & hashes.get(other)) != 0) {
          // Fast inclusion testing failed.
          continue;
        }
        if (other == clause || type(formula, other) != OR) {
          // other doesn't point to a different OR clause
          continue;
        }

        boolean isSubsummed = true;
        for (int j = clause; isSubsummed && j < clause + length; j++) {
          int literal = formula.get(j);
          isSubsummed = solver.watchLists.get(literal).contains(other);
        }

        if (isSubsummed) {
          subsummed.add(other);
        }
      }
    }

    removeSubsummed(solver, subsummed);

    int numRemovedClauses = subsummed.size();
    if (Configure.verbose) {
      if (numRemovedClauses > 0) {
        System.err.print("ss" + numRemovedClauses + ".");
      }
    }
    return !subsummed.isEmpty();
  }

  private static void removeSubsummed(final Solver solver,
                                      final TIntHashSet subsummed) {
    TIntArrayList formula = solver.watchLists.formula();
    TIntIterator it = subsummed.iterator();
    for (int size = subsummed.size(); size > 0; size--) {
      int clause = it.next();
      if (!isClauseRemoved(formula, clause)) {
        solver.watchLists.removeClause(clause);
      }
    }
  }
}
