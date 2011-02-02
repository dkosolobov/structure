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


/**
 * @todo store initial number of variables
 */
public final class Skeleton implements Serializable {
  private static final Logger logger = Logger.getLogger(Skeleton.class);

  private static Comparator clauseComparator =
      new Comparator<Vector<Integer>>() {
    public int compare(Vector<Integer> o1, Vector<Integer> o2) {
      if (o1.size() != o2.size()) {
        return o1.size() - o2.size();
      }
      for (int i = 0; i < o1.size(); ++i) {
        if (o1.get(i) != o2.get(i)) {
          return o1.get(i) - o2.get(i);
        }
      }
      return 0;
    }
  };

  /** Number of variables */
  public int numVariables = -1;
  /** Instance */
  public TIntArrayList clauses = new TIntArrayList();

  /** Returns a measure of difficulty of the instance. */
  public int difficulty() {
    return clauses.size();
  }

  /** Adds a clause  */
  public void add(int[] clause) {
    assert clause.length > 0;
    clauses.add(clause);
    clauses.add(0);
  }

  /** Adds a clause. */
  public void add(int clause0, int... clause) {
    clauses.add(clause0);
    clauses.add(clause);
    clauses.add(0);
  }

  /** Concatenates another instance. */
  public void append(TIntArrayList other) {
    clauses.add(other.toNativeArray());
  }

  /** Concatenates another instance. */
  public void append(Skeleton other) {
    append(other.clauses);
  }

  /** Returns the skeleton as in DIMACS format. */
  public String toString() {
    int numVariables = 0, numClauses = 0;
    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.get(i);
      if (literal == 0) {
        ++numClauses;
      } else {
        numVariables = Math.max(numVariables, Math.abs(literal));
      }
    }
    StringBuffer result = new StringBuffer();
    result.append("p cnf " + numVariables + " " + numClauses + "\n");
    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.get(i);
      result.append(literal == 0 ? "0\n" : literal + " ");
    }
    return result.toString();
  }

  /** Sorts clauses and literals in clauses. */
  public void canonicalize() {
    Vector<Vector<Integer>> clauses = new Vector<Vector<Integer>>();
    clauses.add(new Vector<Integer>());

    for (int i = 0; i < this.clauses.size(); ++i) {
      int literal = this.clauses.get(i);
      if (literal == 0) {
        Collections.sort(clauses.lastElement());
        clauses.add(new Vector<Integer>());
      } else {
        clauses.lastElement().add(literal);
      }
    }
    clauses.remove(clauses.size() - 1);
    Collections.sort(clauses, clauseComparator);

    this.clauses.clear();
    for (int  i = 0; i < clauses.size(); ++i) {
      Vector<Integer> clause = clauses.elementAt(i);
      for (int j = 0; j < clause.size(); ++j) {
        this.clauses.add(clause.elementAt(j));
      }
      this.clauses.add(0);
    }
  }
}
