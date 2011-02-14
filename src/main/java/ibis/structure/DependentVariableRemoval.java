package ibis.structure;

import java.util.Vector;
import gnu.trove.TIntArrayList;

public class DependentVariableRemoval {
  public DependentVariableRemoval(final int numVariables, 
                                  final TIntArrayList formula) {
    Vector<Integer> clauses = new Vector<Integer>();
    int[] counts = new int[numVariables + 1];

    // Finds all clauses
    int start = 0;
    for (int end = 0; end < formula.size(); end++) {
      int u = BitSet.mapZtoN(formula.get(end));
      // mapZtoN orders variable -1, 1, -2, 2
      formula.set(end, u);
      counts[u + 1 >> 1]++;

      if (u == 0) {
        formula.sort(start, end);
        clauses.add(start);
        start = end + 1;
      }
    }

    // And sorts them
    ClauseComparator comparator = new ClauseComparator(formula);
    java.util.Collections.sort(clauses, comparator);


    int numLiterals = 0;
    int numClauses = 0;
    int count = 1;

    System.err.println("c = " + clauses.size());
    System.err.println("e = " + comparator.compare(0, 3));

    for (int i = 0; i < clauses.size(); i++) {
      int clause = clauses.elementAt(i);
      if (i + 1 < clauses.size() && comparator.compare(clause, clauses.elementAt(i + 1)) == 0) {
        count++;
      } else {
        if (findXOR(formula, clauses, i - count + 1, i + 1)) {
          int length = formula.indexOf(clause, 0) - clause;
          // Prints the xor formula
          int[] xor = formula.toNativeArray(clause, length);
          for (int j = 0; j < xor.length; j++) {
            xor[j] = xor[j] + 1 >> 1;
          }
          System.err.println("" + new TIntArrayList(xor));

          numClauses += count;
          numLiterals += count * length;
        }
        count = 1;
      }
    }
    System.err.println("found " + numClauses + " and " + numLiterals);
  
    // Reverses mapZtoN
    for (int end = 0; end < formula.size(); end++) {
      int u = formula.get(end);
      if (u != Solver.REMOVED) {
        formula.set(end, BitSet.mapNtoZ(u));
      }
    }
    return;
  }

  boolean findXOR(TIntArrayList formula, Vector<Integer> clauses, int start, int end) {
    int clause = clauses.get(start);
    int length = formula.indexOf(clause, 0) - clause;
    int size = end - start;
    int requiredSize = 1 << length - 1;

    if (length > 16 || size < requiredSize) {
      // Not enough XOR clauses
      return false;
    }

    int numImpairs = 0;
    for (int i = start; i < end; i++) {
      System.err.println("clause " + clauses.get(i) + " " + formula.subList(clauses.get(i), clauses.get(i) + length));
      int numNegations = countNegations(formula, clauses.get(i));
      numImpairs += numNegations & 1;
    }
    int numPairs = size - numImpairs;

    if (numImpairs >= requiredSize && numImpairs >= requiredSize) {
      System.err.println("found contradiction " + length + " " + requiredSize);
      return true;
    }

    if (numImpairs >= requiredSize) {
      System.err.println("found +");
      return true;
    }

    if (numPairs >= requiredSize) {
      System.err.println("found -");
      return true;
    }

    return false;
  }

  int countNegations(TIntArrayList formula, int clause) {
    int count = 0;
    while (true) {
      int u = formula.get(clause++);
      if (u == 0) {
        break;
      }
      count += u & 1;
    }
    return count;
  }
}


class ClauseComparator implements java.util.Comparator<Integer> {
  private TIntArrayList formula;

  public ClauseComparator(final TIntArrayList formula) {
    this.formula = formula;
  }

  public int compare(Integer o1, Integer o2) {
    final int c1 = o1, c2 = o2;

    // Sorts by length
    int l1 = formula.indexOf(c1, 0) - c1;
    int l2 = formula.indexOf(c2, 0) - c2;
    if (l1 != l2 ) {
      return l1 - l2;
    }

    // Ties are broken by variables
    for (int i = 0; i < l1; i++) {
      int u1 = formula.get(c1 + i);
      int u2 = formula.get(c2 + i);
      // Note: variables are transformed with BitSet.mapZtoN
      int diff = (u1 + 1 >> 1) - (u2 + 1 >> 1);
      if (diff != 0) {
        return diff;
      }
    }

    return 0;
  }
}
