package ibis.structure;

import gnu.trove.TIntArrayList;
import java.io.Serializable;
import org.apache.log4j.Logger;


/**
 * @todo store initial number of variables
 */
public final class Skeleton implements Serializable {
  private static final Logger logger = Logger.getLogger(Skeleton.class);

  public TIntArrayList[] clauses;


  /**
   * Parses a string to produce a skeleton instance
   * The string should be formatted like: 1 &amp; (2 | -3 | 3).
   */
  public static Skeleton parse(String nice) {
    Skeleton skeleton = new Skeleton();

    /* splits nice into clauses and clauses into variables */
    String[] clauses = nice.split(" [&] ");

    /* divides each clause into literals */
    for (String clause: clauses) {
      if (clause.charAt(0) == '(') {
        clause = clause.substring(1, clause.length() - 1);
        String[] literalsAsString = clause.split(" [|] ");
        int[] literalsAsInt = new int[literalsAsString.length];
        for (int i = 0; i < literalsAsString.length; ++i) {
          literalsAsInt[i] = Integer.parseInt(literalsAsString[i]);
        }
        skeleton.add(literalsAsInt);
      } else {
        skeleton.add(Integer.parseInt(clause));
      }
    }

    return skeleton;
  }

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
   * @return number of constraints
   */
  public int numConstraints() {
    return clauses[0].size() + clauses[1].size() + clauses[2].size();
  }

  /**
   * Adds an unit.
   */
  public void add(int unit) {
    clauses[1].add(unit);
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
}
