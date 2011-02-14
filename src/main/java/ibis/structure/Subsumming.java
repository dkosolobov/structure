package ibis.structure;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIterator;

/**
 * Performs clause subsumming.
 *
 * Removes clauses that include other clauses.
 * This is the algorithm implemented in SatELite.
 */
public final class Subsumming {
  /** @return true if any clause was removed. */
  public static boolean run(Solver solver) throws ContradictionException {
    int[] keys = solver.lengths.keys();
    long[] hashes = new long[keys.length];
    TIntIntHashMap index = new TIntIntHashMap();

    // Computes clauses' hashes
    for (int i = 0; i < keys.length; ++i) {
      int start = keys[i];
      index.put(start, i);

      long hash = 0;
      int end = start;
      for (;; end++) {
        int u = solver.clauses.get(end);
        if (u == solver.REMOVED) {
          continue;
        }
        if (u == 0) {
          break;
        }
        hash |= 1L << (Hash.hash(u) >>> 26);
      }
      // To fast check that A is not included in B
      // ~hashes[A] & hashes[B] != 0
      hashes[i] = ~hash;
    }

    // List of subsummed clauses to be removed.
    TIntHashSet subsummed = new TIntHashSet();
    // Same as watchList() but as an array for faster access.
    int[][] watchLists = new int[2 * solver.numVariables + 1][];

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
        int literal = solver.clauses.get(end);
        if (literal == solver.REMOVED) {
          continue;
        }
        if (literal == 0) {
          break;
        }
        int numClauses = solver.watchList(literal).size();
        if (numClauses < minNumClauses) {
          bestLiteral = literal;
          minNumClauses = numClauses;
        }
      }

      if (end - start > 16) {
        // Ignores very long clauses to improve perfomance.
        continue;
      }
      if (solver.watchList(bestLiteral).size() <= 1) {
        // Can't include any other clause.
        continue;
      }

      // Lazy initializes the watchList
      int[] watchList = watchLists[bestLiteral + solver.numVariables];
      if (watchList == null) {
        watchList = solver.watchList(bestLiteral).toArray();
        watchLists[bestLiteral + solver.numVariables] = watchList;
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
          int literal = solver.clauses.get(j);
          if (literal == solver.REMOVED) {
            continue;
          }
          if (literal == 0) {
            break;
          }
          isSubsummed = solver.watchList(literal).contains(clause);
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
      if (!solver.isClauseSatisfied(clause)) {
        solver.removeClause(clause);
      }
    }

    if (Configure.verbose) {
      if (numRemovedClauses > 0) {
        System.err.print("ss" + numRemovedClauses + ".");
      }
    }
    return !subsummed.isEmpty();
  }
}
