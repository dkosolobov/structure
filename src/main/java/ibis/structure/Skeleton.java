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

  /** Returns the skeleton as in extended DIMACS format. */
  public String toString() {
    int numClauses = 0;
    StringBuffer result = new StringBuffer();

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      numClauses++;

      result.append(clauseToString(formula, clause));
      result.append("\n");
    }

    return "p cnf " + numVariables + " " + numClauses + "\n" + result;
  }
}
