package ibis.structure;

import java.util.Collections;
import java.util.Vector;
import gnu.trove.TIntArrayList;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public class XOR {
  private static int REMOVED = Integer.MAX_VALUE;
  private static final Logger logger = Logger.getLogger(Solver.class);

  /** Sorts clauses by length */
  private static final class ClauseLengthComparator
      implements java.util.Comparator<Integer> {
    private TIntArrayList formula;

    public ClauseLengthComparator(final TIntArrayList formula) {
      this.formula = formula;
    }

    public int compare(Integer c1, Integer c2) {
      int l1 = length(formula, c1);
      int l2 = length(formula, c2);
      return l1 - l2;
    }
  }

  /** Sorts clauses by variables */
  private static final class ClauseVariablesComparator
      implements java.util.Comparator<Integer> {
    private TIntArrayList formula;

    public ClauseVariablesComparator(final TIntArrayList formula) {
      this.formula = formula;
    }

    public int compare(Integer c1, Integer c2) {
      int l1 = length(formula, c1);
      int l2 = length(formula, c2);
      for (int i = 0; i < l1 && i < l2; i++) {
        int u1 = var(formula.get(c1 + i));
        int u2 = var(formula.get(c2 + i));
        if (u1 != u2) {
          return u1 - u2;
        }
      }
      return l1 - l2;
    }
  }

  /** Sorts clauses by literals */
  private static final class ClauseLiteralsComparator
      implements java.util.Comparator<Integer> {
    private TIntArrayList formula;

    public ClauseLiteralsComparator(final TIntArrayList formula) {
      this.formula = formula;
    }

    public int compare(Integer c1, Integer c2) {
      int l1 = length(formula, c1);
      int l2 = length(formula, c2);
      for (int i = 0; i < l1 && i < l2; i++) {
        int u1 = formula.get(c1 + i);
        int u2 = formula.get(c2 + i);
        if (u1 != u2) {
          return u1 - u2;
        }
      }
      return l1 - l2;
    }
  }

  public static void extractXORClauses(final Skeleton instance)
      throws ContradictionException {
    TIntArrayList formula = instance.formula;
    TIntArrayList xors = new TIntArrayList();

    ClauseLengthComparator lengthComparator =
        new ClauseLengthComparator(formula);
    ClauseVariablesComparator variablesComparator =
        new ClauseVariablesComparator(formula);

    Vector<Integer> clauses = findAndSortClauses(formula);
    Collections.sort(clauses, variablesComparator);
    Collections.sort(clauses, lengthComparator);

    int count = 1;
    for (int i = 1; i <= clauses.size(); i++) {
      int curr = i == clauses.size() ? -1 : clauses.get(i);
      int prev = clauses.get(i - 1);

      if (curr != -1 && variablesComparator.compare(curr, prev) == 0) {
        count++;
      } else {
        int start = i - count, end = i;
        int isXORClause = isXORClause(formula, clauses, start, end);
        if (isXORClause != 0) {
          int clause = clauses.get(i - count);
          int length = length(formula, clause);

          // Builds the xor clause
          xors.add(encode(length, isXORClause == -1 ? XOR : NXOR));
          for (int j = clause; j < clause + length; j++) {
            xors.add(var(formula.get(j)));
          }

          // logger.info("Found xor gate " + clauseToString(xors, xors.size() - length));

          // Removes the cnf clauses
          for (int j = start; j < end; j++) {
            clause = clauses.get(j);
            boolean odd = hasOddNumNegations(formula, clause);
            if (isXORClause == (odd ? 1 : -1)) {
              // logger.info("removed " + clauseToString(formula, clause));
              removeClause(formula, clause);
            }
          }
        }

        count = 1;
      }
    }
  
    compact(formula);
    formula.add(xors.toNativeArray());
  }

  private static Vector<Integer> findAndSortClauses(
      final TIntArrayList formula) {
    Vector<Integer> clauses = new Vector<Integer>();

    ClauseIterator it;

    it = new ClauseIterator(formula);
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

  private static int isXORClause(final TIntArrayList formula,
                                 final Vector<Integer> clauses,
                                 final int start,
                                 final int end) throws ContradictionException {
    int clause = clauses.get(start);
    int length = length(formula, clause);
    if (length < 3 || length > 24) {
      // Too small or too big for a xor clause.
      return 0;
    }

    int size = end - start;
    int requiredSize = 1 << length - 1;
    if (size < requiredSize) {
      // Not enough XOR clauses
      return 0;
    }

    ClauseLiteralsComparator literalsComparator =
        new ClauseLiteralsComparator(formula);
    Collections.sort(clauses.subList(start, end), literalsComparator);

    int numImpairs = 0, numPairs = 0;
    for (int i = start; i < end; i++) {
      if (i == start || literalsComparator.compare(
            clauses.get(i), clauses.get(i - 1)) != 0) {
        boolean odd = hasOddNumNegations(formula, clauses.get(i));
        numImpairs += odd ? 1 : 0;
        numPairs += odd ? 0 : 1;
      }
    }

    if (numImpairs == requiredSize && numPairs == requiredSize) {
      throw new ContradictionException();
    }
    if (numImpairs == requiredSize) {
      return +1;
    }
    if (numPairs == requiredSize) {
      return -1;
    }
    return 0;
  }

  /** Returns true if formula contains an odd number of negations */
  private static boolean hasOddNumNegations(final TIntArrayList formula,
                                            final int clause) {
    int length = length(formula, clause);
    int count = 0;
    for (int i = clause; i < clause + length; i++) {
      int u = formula.get(i);
      count += (u < 0) ? 1 : 0;
    }
    return (count & 1) != 0;
  }
}
