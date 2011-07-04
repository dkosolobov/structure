package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.map.hash.TIntIntHashMap;

import static ibis.structure.Misc.*;

public final class Skeleton implements java.io.Serializable {
  public int numVariables;
  public TIntArrayList formula;

  /** Constructor. */
  public Skeleton(final int numVariables) {
    this(numVariables, new TIntArrayList());
  }

  /** Constructor. */
  public Skeleton(final int numVariables,
                  final TIntArrayList formula) {
    this.numVariables = numVariables;
    this.formula = formula;
  }

  /** Returns number of literals + number of clauses */
  public int size() {
    return formula.size();
  }

  /** Returns a copy of this instance. */
  public Skeleton clone() {
    return new Skeleton(numVariables, new TIntArrayList(formula));
  }

  /** Expands small XOR gates. */
  public void expandSmallXOR() {
    if (!Configure.xor) {
      return;
    }

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int type = type(formula, clause);

      if (type != OR && length <= 3) {
        for (int i = 0; i < (1 << length); i++) {
          boolean sign = false;
          for (int j = 0; j < length; j++) {
            if ((i & (1 << j)) != 0) {
              sign = !sign;
            }
          }

          if ((type == XOR && !sign) || (type == NXOR && sign)) {
            formula.add(encode(length, OR));
            for (int j = 0; j < length; j++) {
              int literal = formula.getQuick(clause + j);
              if ((i & (1 << j)) != 0) {
                formula.add(neg(literal));
              } else {
                formula.add(literal);
              }
            }
          }
        }

        removeClause(formula, clause);
      }
    }
  }

  /**
   * Computes scores for every literal.
   *
   * Idea for evaluating literals was adapted from:
   * Building a Hybrid SAT Solver via Conflict Driven,
   * Look Ahead and XOR Reasoning Techniques
   *
   * The propagation is aproximated estimated.
   */
  public int[] pickVariables(final TDoubleArrayList global,
                             final int numVariables) {
    ClauseIterator it;

    // Scores all variables.
    TIntIntHashMap scores = new TIntIntHashMap();
    it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int delta = 1 + (128 >> length);
      int extra = length == 2 ? 8 : 0;

      for (int i = clause; i < clause + length; i++) {
        int literal = formula.getQuick(i);
        scores.adjustOrPutValue(literal, delta, delta);
        scores.adjustOrPutValue(neg(literal), extra, extra);
      }
    }

    // Improves scores based on implication graph
    it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);

      if (length == 2) {
        final double coef = 0.8;
        int u = formula.getQuick(clause);
        int v = formula.getQuick(clause + 1);
        scores.put(neg(u), scores.get(neg(u)) + (int) (coef * scores.get(v)));
        scores.put(neg(v), scores.get(neg(v)) + (int) (coef * scores.get(u)));
      }
    }

    // Finds top variables
    int[] top = new int[numVariables];
    double[] cnt = new double[numVariables];
    int num = 0;

    TIntIntIterator it1 = scores.iterator();
    for (int size = scores.size(); size > 0; size--) {
      it1.advance();
      int l = it1.key();

      if (l == var(l)) {
        // A variable's score depends on scores of both phases.
        double p = scores.get(l);
        double n = scores.get(neg(l));

        /*
        final double wc1 = Configure.ttc[0];
        final double wp1 = Configure.ttc[1];
        final double wn1 = Configure.ttc[2];

        final double wc2 = Configure.ttc[3];
        final double wp2 = Configure.ttc[4];
        final double wn2 = Configure.ttc[5];

        final double wc3 = Configure.ttc[6];
        final double wp3 = Configure.ttc[7];
        final double wn3 = Configure.ttc[8];

        double c = 
          + wc1 * (pow(p, wp1) * pow(n, wn1) + pow(n, wp1) * pow(p, wn1))
          + wc2 * (pow(p, wp2) * pow(n, wn2) + pow(n, wp2) * pow(p, wn2))
          + wc3 * (pow(p, wp3) * pow(n, wn3) + pow(n, wp3) * pow(p, wn3));
        */

        double pn = p * n;
        double c = pn * pn * (2. * pn * pn + 2. * pn + p + n);

        if (num < top.length) {
          top[num] = l;
          cnt[num] = c;
          num++;
          continue;
        }
        
        if (c > cnt[top.length - 1]) {
          int pos = top.length - 1;
          for (; pos > 0 && c > cnt[pos - 1]; pos--) {
            top[pos] = top[pos - 1];
            cnt[pos] = cnt[pos - 1];
          }

          top[pos] = l;
          cnt[pos] = c;
        }
      }
    }

    assert num > 0;
    return java.util.Arrays.copyOf(top, num);
  }

  private final double pow(double a, double b) {
    return Math.pow(a, b);
  }

  /** Returns the skeleton as in extended DIMACS format. */
  public String toString() {
    int numClauses = 0;
    StringBuffer result = new StringBuffer();

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      result.append(clauseToString(formula, clause));
      result.append("\n");
      numClauses++;
    }

    return "p cnf " + numVariables + " " + numClauses + "\n" + result;
  }
}
