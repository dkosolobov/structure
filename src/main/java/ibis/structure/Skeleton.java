package ibis.structure;

import gnu.trove.list.array.TIntArrayList;

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

  private static final int MAX_LENGTH = 16;
  private static final double[] wOR = new double[MAX_LENGTH];
  private static final double[] wXOR = new double[MAX_LENGTH];
  private static final double wBIN;

  static {
    final double alpha = Configure.ttc[4];
    final double beta = Configure.ttc[5];
    final double gamma = Configure.ttc[6];

    wOR[0] = alpha;
    wXOR[0] = beta;
    wBIN = gamma;

    for (int i = 1; i < MAX_LENGTH; i++) {
      wOR[i] = wOR[i - 1] * alpha;
      wXOR[i] = wXOR[i - 1] * beta;
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
  public double[] evaluateLiterals() {
    double[] scores = new double[2 * numVariables + 1];
    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int type = type(formula, clause);
      
      if (length >= MAX_LENGTH) {
        continue;
      }

      if (type == OR) {
        double delta = wOR[length];
        for (int i = clause; i < clause + length; i++) {
          int literal = formula.getQuick(i);
          scores[neg(literal) + numVariables] += delta;
        }
      } else {
        double delta = wXOR[length];
        for (int i = clause; i < clause + length; i++) {
          int literal = formula.getQuick(i);
          scores[literal + numVariables] += delta;
          scores[neg(literal) + numVariables] += delta;
        }
      } 

      if (length == 2) {
        int u = formula.get(clause);
        int v = formula.get(clause + 1);

        scores[neg(u) + numVariables] += wBIN * scores[v + numVariables];
        scores[neg(v) + numVariables] += wBIN * scores[u + numVariables];
      }
    }

    return scores;
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
