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

  private static final java.util.Random random = new java.util.Random(1);

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
    ImplicationsGraph graph = new ImplicationsGraph(numVariables);

    // These values were fine tuned for easy instances from
    // SAT Competition 2011.
    final double alpha = 0.64;
    final double beta = 0.59;
    final double gamma = 0.92;

    // final double alpha = Configure.ttc[0];
    // final double beta = Configure.ttc[1];
    // final double gamma = Configure.ttc[2];

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
      } else {
        // TODO: beta doesn't seem to have a big influence.
        delta = Math.pow(beta, length);
        for (int i = clause; i < clause + length; i++) {
          int literal = formula.getQuick(i);
          scores[literal + numVariables] += delta;
          scores[neg(literal) + numVariables] += delta;
        }
      } 

      if (length == 2) {
        int u = formula.getQuick(clause);
        int v = formula.getQuick(clause + 1);
        graph.add(neg(u), v);
      }
    }

    int[] sort = graph.topologicalSort();
    for (int i = 0; i < sort.length; i++) {
      int u = sort[i];
      if (u == 0) {
        continue;
      }

      TIntArrayList edges = graph.edges(u);
      for (int j = 0; j < edges.size(); j++) {
        int v = edges.getQuick(j);
        scores[u + numVariables] += gamma * scores[v + numVariables];
      }
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
