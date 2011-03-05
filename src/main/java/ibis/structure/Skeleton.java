package ibis.structure;

import gnu.trove.list.array.TIntArrayList;

import static ibis.structure.Misc.*;


public final class Skeleton implements java.io.Serializable {
  public int numVariables;
  public TIntArrayList formula;
  public TIntArrayList bins;

  /** Constructor. */
  public Skeleton(final int numVariables) {
    this(numVariables, new TIntArrayList(), new TIntArrayList());
  }

  /** Constructor. */
  public Skeleton(final int numVariables,
                  final TIntArrayList formula) {
    this(numVariables, formula, new TIntArrayList());
  }

  /** Constructor. */
  public Skeleton(final int numVariables,
                  final TIntArrayList formula,
                  final TIntArrayList bins) {
    this.numVariables = numVariables;
    this.formula = formula;
    this.bins = bins;
  }

  /** Returns number of literals + number of clauses */
  public int size() {
    return formula.size() + 3 * bins.size() / 2;
  }

  /**
   * Computes scores for every literal.
   *
   * Idea for evaluating literals was adapted from:
   * Building a Hybrid SAT Solver via Conflict Driven, Look Ahead and XOR Reasoning Techniques
   *
   * The propagation is aproximated estimated.
   */
  public double[] evaluateLiterals() {
    double[] scores = new double[2 * numVariables + 1];

    // These values were fine tuned for easy instances from
    // SAT Competition 2011.
    final double alpha = 0.54;
    final double beta = 0.55;
    final double gamma = 0.38;

    // First scores are computed based on clauses length
    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int type = type(formula, clause);
      double delta;

      if (type == OR) {
        delta = Math.pow(alpha, length);
        for (int i = clause; i < clause + length; i++) {
          int literal = formula.getQuick(i);
          scores[neg(literal) + numVariables] += delta;
        }
      } 

      if (type != OR) {
        // TODO: beta doesn't seem to have a big influence.
        delta = Math.pow(beta, length);
        for (int i = clause; i < clause + length; i++) {
          int literal = formula.getQuick(i);
          scores[literal + numVariables] += delta;
          scores[neg(literal) + numVariables] += delta;
        }
      } 
    }
    
    for (int i = 0; i < bins.size(); i += 2) {
      int u = bins.getQuick(i);
      int v = bins.getQuick(i + 1);

      double delta = Math.pow(alpha, 2);
      scores[neg(u) + numVariables] += delta;
      scores[neg(v) + numVariables] += delta;
    }

    // Second add scores up on implication graph
    for (int i = 0; i < bins.size(); i += 2) {
      int u = bins.getQuick(i);
      int v = bins.getQuick(i + 1);
      scores[neg(u) + numVariables] += gamma * scores[v + numVariables];
    }

    return scores;
  }

  public double evaluateBranch(double[] scores, int branch) {
    double p = scores[branch + numVariables];
    double n = scores[neg(branch) + numVariables];
    return 1024 * n * p + n + p;
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
