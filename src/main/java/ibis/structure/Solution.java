package ibis.structure;

import java.io.PrintStream;
import gnu.trove.TIntCollection;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TIntDoubleIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntDoubleHashMap;
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
  /** List of units. */
  private TIntArrayList units = null;
  /** Learned clauses tree. */
  public TIntArrayList learned = EMPTY;

  /** Returns a solution representing a satisfiable instance. */
  public static Solution satisfiable(final TIntCollection units) {
    Solution solution = new Solution(SATISFIABLE);
    solution.units = new TIntArrayList(units);
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
    for (int unit : core.units().toArray()) {
      if (unit != branch) {
        empty = false;
        solution.learned.add(unit);
        solution.learned.add(0);
      }
    }

    /*
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
    */

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
      assert s1.isUnsatisfiable();
      assert s1.learned.size() == 2: "Learned size is " + s1.learned.size();
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

  /** Returns an array of units */
  public TIntArrayList units() {
    return units;
  }

  /** Returns the tree of learned clauses. */
  public TIntArrayList learned() {
    return learned;
  }

  /** Returns a string representation of this solution. */
  public String toString() {
    switch (solved) {
      case SATISFIABLE:
        return "satisfiable " + new TIntArrayList(units);

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
      for (int i = 0; i < units.size(); i++) {
         out.print(" " + units.get(i));
      }
      out.println(" 0");
    }
    out.flush();
  }

  /** Adds learned clauses to formula. */
  public void addLearnedClauses(final TIntArrayList formula,
                                final TIntDoubleHashMap histogram) {
    int size = formula.size();
    logger.info("Learned size is " + learned.size());

    addLearnedClauses(formula, 0, new TIntArrayList(), histogram, 1.);
    logger.info("Added " + (formula.size() - size) + " new literals");
  }

  /** Expands the tree of learned clauses and adds them to formula. */
  private int addLearnedClauses(final TIntArrayList formula, 
                                final int start,
                                final TIntArrayList stack,
                                final TIntDoubleHashMap histogram,
                                final double score) {
    int p = start;
    if (p == learned.size()) {
      return p;
    }

    if (learned.get(p) == 0) {
      if (stack.size() <= 15) {
        double alpha = Configure.ttc[0];
        double beta = Configure.ttc[1];
        double delta;

        delta = 1.;
        for (int i = stack.size() - 1; i >= 0; i--) {
          delta *= alpha;
          histogram.adjustOrPutValue(stack.get(i), delta, delta);
        }

        delta = 1.;
        for (int i = 0; i < stack.size(); i++) {
          delta *= beta;
          histogram.adjustOrPutValue(stack.get(i), delta, delta);
        }

        formula.add(encode(stack.size(), OR));
        formula.addAll(stack);
      }
      return p + 1;
    }

    while (p < learned.size()) {
      int l = learned.get(p);
      if (l == 0) {
        return p + 1;
      }

      // histogram.adjustOrPutValue(l, score, score);
      stack.add(l);
      p = addLearnedClauses(formula, p + 1, stack, histogram, score * 0.8);
      stack.removeAt(stack.size() - 1);
    }

    return learned.size();
  }
}
