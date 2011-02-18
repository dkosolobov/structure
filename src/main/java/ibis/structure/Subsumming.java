package ibis.structure;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntLongHashMap;
import gnu.trove.TIntIterator;
import gnu.trove.TIntArrayList;
import gnu.trove.TLongArrayList;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/**
 * Performs clause subsumming.
 *
 * Removes clauses that include other clauses.
 * This is the algorithm implemented in SatELite.
 */
public final class Subsumming {
  private static final Logger logger = Logger.getLogger(Subsumming.class);

  /** Number of variables */
  private final int numVariables;
  /** Watch lists */
  private final WatchLists watchLists;
  /** Formula */
  private final TIntArrayList formula;
  /** Maps clauses to their hash */
  private final TIntLongHashMap hashes = new TIntLongHashMap();
  /** Used to check if a clause is included in another clause */
  private final TouchSet visited;
  /** Number of removed clauses */
  private int numRemovedClauses = 0;

  /** Constructor */
  public Subsumming(final Solver solver) {
    numVariables = solver.numVariables;
    watchLists = solver.watchLists;
    formula = watchLists.formula();
    visited = new TouchSet(numVariables);
  }

  /** @return true if any clause was removed. */
  public boolean run() throws ContradictionException {
    TIntArrayList prevIndex = new TIntArrayList();
    int[] last = new int[2 * numVariables + 1];
    java.util.Arrays.fill(last, -1);
    TIntArrayList clauses = new TIntArrayList();

    // Computes clauses' hashes and bestLiterals.
    ClauseIterator it;
    it = new ClauseIterator(formula);
    for (int i = 0; it.hasNext(); i++) {
      int clause = it.next();
      int length = length(formula, clause);

      clauses.add(clause);
      hashes.put(clause, clauseHash(clause, length));

      int bestLiteral = bestLiteral(clause, length);
      prevIndex.add(last[bestLiteral + numVariables]);
      last[bestLiteral + numVariables] = i;
    }

    // For each literal the list of clauses where it is the
    // best literal is encoded in last, prevIndex
    TIntArrayList uClauses = new TIntArrayList();
    TLongArrayList uHashes = new TLongArrayList();
    for (int u = -numVariables; u <= numVariables; ++u) {
      uClauses.reset();
      uHashes.reset();

      TIntIterator it1 = watchLists.get(u).iterator();
      for (int size =  watchLists.get(u).size(); size > 0; size--) {
        int clause = it1.next();
        uClauses.add(clause);
        uHashes.add(hashes.get(clause));
      }

      int index = last[u + numVariables];
      while (index != -1) {
        int clause = clauses.get(index);
        if (!isClauseRemoved(formula, clause)) {
          findSubsummed(clause, hashes.get(clause), uClauses, uHashes);
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


  private void findSubsummed(final int clause, final long hash,
                             final TIntArrayList clauses,
                             final TLongArrayList hashes) {
    int length = length(formula, clause);
    int type = type(formula, clause);

    visited.reset();
    for (int j = clause; j < clause + length; j++) {
      visited.add(formula.getQuick(j));
    }

    for (int i = 0; i < clauses.size(); i++) {
      int other = clauses.getQuick(i);
      long otherHash = hashes.getQuick(i);

      if ((hash & ~otherHash) != 0) {
        continue;
      }
      if (isClauseRemoved(formula, other)) {
        continue;
      }
      if (other == clause) {
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

      if (type == OR && otherType == OR) {
        numRemovedClauses++;
        watchLists.removeClause(other);
      } else if (type != OR && otherType != OR) {
        // TODO: this is can be somehow faster
        for (int j = clause; j < clause + length; j++) {
          int literal = formula.getQuick(j);
          watchLists.removeLiteral(other, literal);
        }

        if (type == XOR) {
          switchXOR(formula, other);
        }
      }
    }
  }

  /**
   * To fast check that A is not included in B
   * hashes[A] &amp; ~hashes[B] != 0
   */
  private long clauseHash(final int clause, final int length) {
    long hash = 0;
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
  private int bestLiteral(final int clause, final int length) {
    int bestLiteral = 0;
    int minNumClauses = Integer.MAX_VALUE;

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
