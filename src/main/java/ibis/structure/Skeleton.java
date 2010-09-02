package ibis.structure;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import java.io.Serializable;
import org.apache.log4j.Logger;

/**
 * @todo store initial number of variables
 */
public final class Skeleton implements Serializable {
  private static final Logger logger = Logger.getLogger(Skeleton.class);

  public TIntArrayList[] clauses;

  /**
   * Constructor.
   */
  public Skeleton() {
    this.clauses = new TIntArrayList[] {
       new TIntArrayList(),  // clauses, delimited by 0
       new TIntArrayList(),  // units
       new TIntArrayList(),  // binaries
    };
  }

  /**
   * @return number of units
   */
  public int numUnits() {
    return clauses[1].size();
  }

  /**
   * @return a simple difficulty evaluator.
   */
  public int difficulty() {
    return clauses[0].size() + clauses[1].size() + clauses[2].size();
  }

  /**
   * Adds a clause.
   */
  public void add(int[] clause) {
    assert clause.length > 0;
    if (clause.length == 1) {
      clauses[1].add(clause[0]);
    } else if (clause.length == 2) {
      clauses[2].add(clause);
    } else if (clause.length >= 3) {
      clauses[0].add(clause);
      clauses[0].add(0);
    }
  }

  /**
   * Canonicalize instance.
   *
   * Removes duplicate variables from clauses.
   * Detects some simple contradictions like a &amp; -a.
   * Simplifies clauses if literals are known, however clauses
   * are passed only once.
   *
   * Binaries and clauses will appear in the same order with same
   * literal order except, of course, for the removed literals.
   */
  public void canonicalize()
      throws ContradictionException {
    TIntHashSet units = new TIntHashSet(clauses[1].toNativeArray());

    TIntArrayList binaries = new TIntArrayList();
    for (int i = 0; i < clauses[2].size(); i += 2) {
      int u = clauses[2].get(i);
      int v = clauses[2].get(i + 1);
      if (u == -v) {
      } else if (u == v) {
        if (units.contains(u)) {
        } else if (units.contains(-u)) {
          throw new ContradictionException();
        } else {
          units.add(u);
        }
      } else if (units.contains(u) || units.contains(v)) {
      } else if (units.contains(-u)) {
        if (units.contains(-v)) {
          throw new ContradictionException();
        } else {
          units.add(v);
        }
      } else if (units.contains(-v)) {
        units.add(u);
      } else {
        binaries.add(u);
        binaries.add(v);
      }
    }

    TIntArrayList clauses = new TIntArrayList();
    TIntArrayList clause = new TIntArrayList();
    boolean satisfied = false;
    for (int i = 0; i < this.clauses[0].size(); ++i) {
      int literal = this.clauses[0].get(i);
      if (literal == 0) {
        if (!satisfied) {
          if (clause.size() == 0) {
            throw new ContradictionException();
          } else if (clause.size() == 1) {
            units.add(clause.get(0));
          } else if (clause.size() == 2) {
            binaries.add(clause.get(0));
            binaries.add(clause.get(1));
          } else {
            clauses.add(clause.toNativeArray());
            clauses.add(0);
          }
        }
        clause.clear();
        satisfied = false;
      } else if (!satisfied) {
        if (units.contains(literal) || clause.contains(-literal)) {
          satisfied = true;
        } else if (!units.contains(-literal) && !clause.contains(literal)) {
          clause.add(literal);
        }
      }
    }

    TIntIterator it;
    for (it = units.iterator(); it.hasNext(); ) {
      if (units.contains(-it.next())) {
        throw new ContradictionException();
      }
    }

    this.clauses[0] = clauses;
    this.clauses[1] = new TIntArrayList(units.toArray());
    this.clauses[2] = binaries;
  }
}
