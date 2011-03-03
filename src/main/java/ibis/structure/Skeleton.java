package ibis.structure;

import gnu.trove.list.array.TIntArrayList;

import static ibis.structure.Misc.*;


public final class Skeleton implements java.io.Serializable {
  public int numVariables;
  public TIntArrayList formula = new TIntArrayList();

  /** Constructor. */
  public Skeleton(final int numVariables) {
    this.numVariables = numVariables;
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
