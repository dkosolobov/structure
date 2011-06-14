package ibis.structure;

import java.io.PrintStream;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TIntArrayList;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

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
  private static final Logger logger = Logger.getLogger(RestartActivity.class);

  private static final int SATISFIABLE = 10;
  private static final int UNSATISFIABLE = 20;
  private static final int UNKNOWN = 30;
  private static final TIntArrayList EMPTY = new TIntArrayList();

  /** One of: SATISFIABLE, UNSATISFIABLE or UNKNOWN */
  private int solved = UNKNOWN;
  /** Units. */
  private int[] units = null;
  /** Learned. */
  private TIntArrayList learned = EMPTY;

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

  public static Solution unsatisfiable(final int branch) {
    assert branch != 0;
    Solution solution = new Solution(UNSATISFIABLE);
    solution.learned = new TIntArrayList();
    solution.learned.add(neg(branch));
    solution.learned.add(0);
    return solution;
  }

  /** Returns a solution representing an unknown instance. */
  public static Solution unknown() {
    return new Solution(UNKNOWN);
  }

  public static Solution unknown(final int branch,
                                 final Solution s,
                                 final Core core) {
    assert branch != 0;
    Solution solution = new Solution(UNKNOWN);

    solution.learned = new TIntArrayList();
    solution.learned.add(neg(branch));
    solution.learned.addAll(s.learned);
    boolean empty = s.learned.isEmpty();

    // Adds units.
    TIntIterator it = core.units().iterator();
    for (int size = core.units().size(); size > 0; size--) {
      int unit = it.next();
      if (unit != branch) {
        empty = false;
        solution.learned.add(unit);
        solution.learned.add(0);
      }
    }

    // Adds proxies.
    int numVariables = core.numVariables();
    int[] proxies = core.proxies();
    for (int l = 1; l <= numVariables; l++) {
      if (proxies[l + numVariables] != l) {
        empty = false;

        solution.learned.add(l);
        solution.learned.add(neg(proxies[l + numVariables]));
        solution.learned.add(0);
        solution.learned.add(0);

        solution.learned.add(neg(l));
        solution.learned.add(proxies[l + numVariables]);
        solution.learned.add(0);
        solution.learned.add(0);
      }
    }

    if (empty) {
      solution.learned = EMPTY;
    } else {
      solution.learned.add(0);
    }

    return solution;
  }

  public static Solution unknown(final Solution s1, final Solution s2) {
    if (s1.isUnknown() && s2.isUnsatisfiable()) {
      return unknown(s2, s1);
    }

    if (s2.learned.isEmpty()) {
      Solution solution = new Solution(UNKNOWN);
      solution.learned = s1.learned;
      return solution;
    }

    Solution solution = new Solution(UNKNOWN);
    solution.learned = new TIntArrayList();

    if (s1.isUnknown()) {
      assert s2.isUnknown();
      solution.learned.addAll(s1.learned);
      solution.learned.addAll(s2.learned);
    } else {
      assert s1.learned.size() == 2;
      solution.learned.add(s1.learned.get(0));
      solution.learned.addAll(s2.learned);
      solution.learned.set(1, 0);
      solution.learned.removeAt(solution.learned.size() - 1);
    }

    return solution;
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

  public int exitcode() {
    switch (solved) {
      case SATISFIABLE:
        return 10;
      case UNSATISFIABLE:
        return 20;
      case UNKNOWN:
        return 0;
    }
    assert false;
    return -1;
  }

  public int[] units() {
    return units;
  }

  public TIntArrayList learned() {
    return learned;
  }

  public String toString() {
    switch (solved) {
      case SATISFIABLE:
        return "satisfiable";

      case UNSATISFIABLE:
        return "unsatisfiable";

      case UNKNOWN:
        return "unknown";
    }

    return "invalid";
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
    out.flush();
  }

  /** Adds learned clauses to formula. */
  public void addLearnedClauses(final TIntArrayList formula) {
    int size = formula.size();
    addLearnedClauses(formula, 0, new TIntArrayList());
    logger.info("Learned size is " + learned.size());
    logger.info("Added " + (formula.size() - size) + " new literals");
  }

  private int addLearnedClauses(final TIntArrayList formula, 
                                int start,
                                final TIntArrayList stack) {
    if (start == learned.size()) {
      return start;
    }

    if (learned.get(start) == 0) {
      if (stack.size() <= 7) {
        formula.add(encode(stack.size(), OR));
        formula.addAll(stack);
      }
      return start + 1;
    }

    while (start < learned.size()) {
      int l = learned.get(start);
      if (l == 0) {
        return start + 1;
      }

      stack.add(l);
      start = addLearnedClauses(formula, start + 1, stack);
      stack.removeAt(stack.size() - 1);
    }

    return learned.size();
  }

  public int show(String path, int start, int depth) {
    if (start == learned.size()) {
      return start;
    }
    if (learned.get(start) == 0) {
      if (depth <= 3) {
        System.err.println(path);
      }
      return start + 1;
    }

    while (start < learned.size()) {
      int l = learned.get(start);
      if (l == 0) return start + 1;
      start = show(path + " " + l, start + 1, depth + 1);
    }

    return learned.size();
  }
}
