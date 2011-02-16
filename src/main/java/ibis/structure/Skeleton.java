package ibis.structure;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;;
import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class Skeleton implements Serializable {
  public int numVariables;
  public TIntArrayList formula = new TIntArrayList();

  /** Constructor. */
  public Skeleton(int numVariables) {
    this.numVariables = numVariables;
  }

  /** Returns the skeleton as in DIMACS format. */
  public String toString() {
    int numClauses = 0;
    StringBuffer result = new StringBuffer();

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();

      result.append(clauseToString(formula, clause));
      result.append("\n");
    }

    return result.toString();
  }
}
