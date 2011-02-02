package ibis.structure;

import java.io.PrintStream;

import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;


/**
 * Solution represents the solution for an instance.
 *
 * There are three posibilities
 *
 * <ul>
 * <li><tt>SATISFIABLE</tt> A solution was found.</li>
 * <li><tt>UNSATISFIABLE</tt> Instance is unsatisfiable.</li>
 * <li><tt>UNKNOWN</tt> a solution was not found, more branching is required.</li>
 * </ul>
 */
public final class Solution {
  private static final int SATISFIABLE = 10;
  private static final int UNSATISFIABLE = 20;
  private static final int UNKNOWN = 30;

  /** One of: SATISFIABLE, UNSATISFIABLE or UNKNOWN */
  private int solved = UNKNOWN;
  /** Units. */
  private int[] units = null;


  /** Returns a solution representing a satisfiable instance. */
  public static Solution satisfiable(final int[] units) {
    Solution solution = new Solution(SATISFIABLE);
    solution.units = units;
    return solution;
  }

  /** Returns a solution representing an unsatifiable instance. */
  public static Solution unsatisfiable() {
    return new Solution(UNSATISFIABLE);
  }

  /** Returns a solution representing an unknown instance. */
  public static Solution unknown() {
    return new Solution(UNKNOWN);
  }

  private Solution(final int solved) {
    this.solved = solved;
  }

  public boolean isSatisfiable() {
    return solved == SATISFIABLE;
  }

  public boolean isUnsatisfiable() {
    return solved == UNSATISFIABLE;
  }

  public boolean isUnknown() {
    return solved == UNKNOWN;
  }

  public int[] units() {
    return units;
  }

  /** Prints solution to out.  */
  public void print(PrintStream out) {
    switch (solved) {
      case SATISFIABLE:
        out.println("s SATISFIABLE");
        break;

      case UNSATISFIABLE:
        out.println("s UNSATISFIABLE");
        break;

      case UNKNOWN:
        out.println("s UNKNOWN");
        break;
    }

    if (isSatisfiable()) {
      out.print("v");
      for (int i = 0; i < units.length; ++i) {
         out.print(" " + units[i]);
      }
      out.println(" 0");
    }
  }


}
