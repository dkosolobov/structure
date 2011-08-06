package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.map.hash.TIntIntHashMap;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

public final class Skeleton implements java.io.Serializable {
  protected static final Logger logger = Logger.getLogger(Skeleton.class);

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

  /** Returns a set of variables. */
  public TIntHashSet variables() {
    TIntHashSet tmp = new TIntHashSet();
    ClauseIterator it = new ClauseIterator(formula);

    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      for (int i = clause; i < clause + length; i++) {
        tmp.add(var(formula.getQuick(i)));
      }
    }

    return tmp;
  }

  /** Returns a hash of this instance. */
  public int hash() {
    int hash = 0;

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);

      for (int i = clause; i < clause + length; i++) {
        hash ^= Misc.hash(formula.get(i)) * length;
      }
    }

    return hash;
  }

  /** Expands small XOR gates. */
  public void expandSmallXOR() {
    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int type = type(formula, clause);

      if (type != OR) {
        if (length >= 6) {
          logger.warn("Found large XOR gate with " + length + " inputs");
        }

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

    if (numVariables == 0) {
      return new int[0];
    }

    // Scores all variables.
    TIntIntHashMap scores = new TIntIntHashMap();
    it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int delta = 1 + (57 >> length);
      int extra = length == 2 ? 29 : 0;

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
        final double coef = 0.5433;
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
        double p0 = 1., p1 = scores.get(l);
        double n0 = 1., n1 = scores.get(neg(l));

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
          + wc1 * (pow(p1, wp1) * pow(n1, wn1) + pow(n1, wp1) * pow(p1, wn1))
          + wc2 * (pow(p1, wp2) * pow(n1, wn2) + pow(n1, wp2) * pow(p1, wn2))
          + wc3 * (pow(p1, wp3) * pow(n1, wn3) + pow(n1, wp3) * pow(p1, wn3));
        */

        double p2 = p1 * p1, p3 = p2 * p1, p4 = p3 * p1,
               p5 = p4 * p1, p6 = p5 * p1, p7 = p6 * p1;
        double n2 = n1 * n1, n3 = n2 * n1, n4 = n3 * n1,
               n5 = n4 * n1, n6 = n5 * n1, n7 = p6 * n1;

        double c = 
          + 81 * (p3 * n2 + n3 * p2)
          + 34 * (p0 * n7 + n0 * p7)
          + 16 * (p6 * n3 + n6 * p3);

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
