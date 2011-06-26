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

  /** One of: SATISFIABLE, UNSATISFIABLE or UNKNOWN */
  private int solved = UNKNOWN;
  /** List of units. */
  private TIntArrayList units = null;
  /** Learned clauses tree. */
  public TIntArrayList learned = new TIntArrayList();

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

    if (!core.units().isEmpty()) {
      solution.learned.add(neg(branch));
      solution.learned.addAll(s.learned);
      solution.learnUnits(core.units(), branch);
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

  /** Learns a new set of units. */
  public void learnUnits(final TIntArrayList units, final int branch) {
    assert isUnknown();
    for (int i = 0; i < units.size(); i++) {
      int unit = units.get(i);
      if (unit != branch) {
        learned.add(unit);
        learned.add(0);
      }
    }
  }

  /** Verifies learned clauses. */
  public void verifyLearned() throws Exception {
    verifyLearned(0, new TIntArrayList());
  }

  private int verifyLearned(final int start,
                            final TIntArrayList stack)
      throws Exception {
    int p = start;
    if (p == learned.size()) {
      return p;
    }

    if (learned.get(p) == 0) {
      for (int i = 0; i < stack.size(); i++) {
        for (int j = i + 1; j < stack.size(); j++) {
          if (var(stack.get(i)) == var(stack.get(j))) {
            throw new Exception(
                "Duplicate variable " + var(stack.get(i))
                + " in learned clause " + stack + " on " + learned);
          }
        }
      }

      return p + 1;
    }

    while (p < learned.size()) {
      int l = learned.get(p);
      if (l == 0) {
        return p + 1;
      }

      stack.add(l);
      p = verifyLearned(p + 1, stack);
      stack.removeAt(stack.size() - 1);
    }

    return learned.size();
    
  }

  /** Adds learned clauses to formula. */
  public void addLearnedClauses(final TIntArrayList formula,
                                final TIntDoubleHashMap histogram) {
    int size = formula.size();
    logger.info("Learned size is " + learned.size());

    addLearnedClauses(formula, 0, new TIntArrayList(), histogram);
    logger.info("Added " + (formula.size() - size) + " new literals");
  }

  /** Expands the tree of learned clauses and adds them to formula. */
  private int addLearnedClauses(final TIntArrayList formula, 
                                final int start,
                                final TIntArrayList stack,
                                final TIntDoubleHashMap histogram) {
    int p = start;
    if (p == learned.size()) {
      return p;
    }

    if (learned.get(p) == 0) {
      if (stack.size() < 8) {
        double alpha = Configure.ttc[0];
        double beta = Configure.ttc[1];
        double gamma = Configure.ttc[2];
        double delta = sigmoid(alpha + beta * stack.size());

        for (int i = stack.size() - 1; i >= 0; i--) {
          histogram.adjustOrPutValue(stack.get(i), delta, delta);
          delta *= gamma;
        }

        // logger.info("Learned " + stack);
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

      stack.add(l);
      p = addLearnedClauses(formula, p + 1, stack, histogram);
      stack.removeAt(stack.size() - 1);
    }

    return learned.size();
  }




  public static double sigmoid(double x) {
    return 1 / (1 + Math.exp(-x));
  }
}
