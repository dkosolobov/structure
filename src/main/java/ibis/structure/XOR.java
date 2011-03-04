package ibis.structure;

import java.util.Collections;
import java.util.Vector;
import gnu.trove.list.array.TIntArrayList;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public class XOR {
  private static int REMOVED = Integer.MAX_VALUE;
  private static final Logger logger = Logger.getLogger(Solver.class);

  /**
   * Finds and removes XOR gates from formula.
   *
   * @param formula containing XOR gates encoded in CNF.
   * @return a formula containing removed XOR gates.
   */
  public static TIntArrayList extractGates(final TIntArrayList formula)
      throws ContradictionException {
    TIntArrayList xorGates = new TIntArrayList();

    ClauseLengthComparator lengthComparator =
        new ClauseLengthComparator(formula);
    ClauseVariablesComparator variablesComparator =
        new ClauseVariablesComparator(formula);
    ClauseLiteralsComparator literalsComparator =
        new ClauseLiteralsComparator(formula);

    // Collections.sort is stable. Using radix sort clauses are
    // sorted by length, then by variables and then by literals.
    Vector<Integer> clauses = getAndSortClauses(formula);
    long start_ = System.currentTimeMillis();
    Collections.sort(clauses, literalsComparator);
    Collections.sort(clauses, variablesComparator);
    Collections.sort(clauses, lengthComparator);
    long end_ = System.currentTimeMillis();
    logger.info("Sorting took " + (end_ - start_) / 1000. + " seconds");

    int numXORGates = 0;
    int count = 1;

    for (int i = 1; i <= clauses.size(); i++) {
      int curr = i == clauses.size() ? -1 : clauses.get(i);
      int prev = clauses.get(i - 1);

      if (curr != -1 && variablesComparator.compare(curr, prev) == 0) {
        count++;
      } else {
        int start = i - count, end = i;
        int isXORClause = isXORClause(formula, clauses, start, end);
        if (isXORClause != OR) {
          int clause = clauses.get(i - count);
          int length = length(formula, clause);

          // Builds the xor clause
          numXORGates++;
          xorGates.add(encode(length, isXORClause));
          for (int j = clause; j < clause + length; j++) {
            xorGates.add(var(formula.getQuick(j)));
          }

          // Removes the cnf clauses
          for (int j = start; j < end; j++) {
            clause = clauses.get(j);
            boolean odd = hasOddNumNegations(formula, clause);
            if ((odd && isXORClause == NXOR) || (!odd && isXORClause == XOR)) {
              // logger.info("removed " + clauseToString(formula, clause));
              removeClause(formula, clause);
            }
          }
        }

        count = 1;
      }
    }

    if (numXORGates > 0) {
      logger.info("Found " + numXORGates + " xor gates");
    }

    compact(formula);
    return xorGates;
  }

  /**
   * Returns a vector with all clauses in formula.
   *
   * Variables are sorted inside clauses.
   */
  private static Vector<Integer> getAndSortClauses(final TIntArrayList formula) {
    Vector<Integer> clauses = new Vector<Integer>();

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);

      // mapZtoN orders variable 0, -1, 1, -2, 2
      // so library sort function can be used.
      for (int i = clause; i < clause + length; i++) {
        formula.set(i, BitSet.mapZtoN(formula.get(i)));
      }

      formula.sort(clause, clause + length);
      clauses.add(clause);

      for (int i = clause; i < clause + length; i++) {
        formula.set(i, BitSet.mapNtoZ(formula.get(i)));
      }
    }

    return clauses;
  }

  /**
   * Checks if CNF clauses from start to end code a XOR gate.
   *
   * @return XOR, NXOR or OR
   */
  private static int isXORClause(final TIntArrayList formula,
                                 final Vector<Integer> clauses,
                                 final int start,
                                 final int end) throws ContradictionException {
    int clause = clauses.get(start);
    int length = length(formula, clause);
    if (length < 3 || length > 24) {
      // Too small or too big for a xor clause.
      return OR;
    }

    int size = end - start;
    int requiredSize = 1 << length - 1;
    if (size < requiredSize) {
      // Not enough XOR clauses
      return OR;
    }

    ClauseLiteralsComparator literalsComparator =
        new ClauseLiteralsComparator(formula);

    int numImpairs = 0, numPairs = 0;
    for (int i = start; i < end; i++) {
      if (i == start || literalsComparator.compare(
            clauses.get(i), clauses.get(i - 1)) != 0) {
        if (hasOddNumNegations(formula, clauses.get(i))) {
          numImpairs += 1;
        } else {
          numPairs += 1;
        }
      }
    }

    if (numImpairs == requiredSize && numPairs == requiredSize) {
      throw new ContradictionException();
    }
    if (numImpairs == requiredSize) {
      return NXOR;
    }
    if (numPairs == requiredSize) {
      return XOR;
    }
    return OR;
  }

  /** Returns true if formula contains an odd number of negations */
  private static boolean hasOddNumNegations(final TIntArrayList formula,
                                            final int clause) {
    int length = length(formula, clause);
    boolean odd = false;
    for (int i = clause; i < clause + length; i++) {
      int u = formula.getQuick(i);
      odd = odd ^ (u < 0);
    }
    return odd;
  }
}
