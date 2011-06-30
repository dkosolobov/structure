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
    Solution solution = new Solution(UNSATISFIABLE);
    if (branch != 0) {
      solution.learned.add(neg(branch));
      solution.learned.add(0);
    }
    return solution;
  }

  /** Returns a solution representing an unknown instance. */
  public static Solution unknown() {
    return new Solution(UNKNOWN);
  }

  public static Solution unknown(final int branch,
                                 final Solution s,
                                 final Core core,
                                 final boolean learnUnits,
                                 final boolean learnProxies) {
    Solution solution = new Solution(UNKNOWN);

    if (!s.learned.isEmpty()
        || (learnUnits && !core.units().isEmpty())
        || (learnProxies && !core.proxies().isEmpty())) {
      if (branch != 0) {
        solution.learned.add(neg(branch));
      }
      if (learnUnits) {
        solution.learnUnits(core.units(), branch);
      }
      if (learnProxies) {
        solution.learnProxies(core.proxies(), branch);
      }
      solution.learned.addAll(s.learned);
      if (branch != 0) {
        solution.learned.add(0);
      }
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
      int u = units.getQuick(i);
      if (var(u) != var(branch)) {
        learned.add(u);
        learned.add(0);
      }
    }
  }

  /** Learns a new set of proxies. */
  public void learnProxies(final TIntArrayList proxies, final int branch) {
    assert isUnknown();
    for (int i = 0; i < proxies.size(); i += 2) {
      int u = proxies.getQuick(i);
      int v = proxies.getQuick(i + 1);

      if (var(u) != var(branch) && var(v) != var(branch)) {
        learned.add(neg(u));
        learned.add(v);
        learned.add(0);
        learned.add(0);

        learned.add(neg(v));
        learned.add(u);
        learned.add(0);
        learned.add(0);
      }
    }
  }

  /** Learns a new set of clauses. */
  public void learnClauses(final TIntArrayList formula) {
    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);

      for (int i = clause; i < clause + length; i++) {
        learned.add(formula.getQuick(i));
      }
      for (int i = clause; i < clause + length; i++) {
        learned.add(0);
      }
    }
  }

  /** Verifies learned clauses. */
  public void verifyLearned() throws Exception {
    int start = 0;
    TIntArrayList stack = new TIntArrayList();
    while (start < learned.size()) {
      start = verifyLearned(start, stack);
    }
  }

  private int verifyLearned(final int start,
                            final TIntArrayList stack) throws Exception {
    if (learned.get(start) == 0) {
      throw new Exception("Unexpected zero at " + start + " in " + learned);
      
    }

    stack.add(learned.get(start));
    int p = start + 1;

    if (p >= learned.size()) {
      throw new Exception("Unexpected end of tree at " + p + " in " + learned);
    }

    if (learned.get(p) == 0) {
      for (int i = 0; i < stack.size(); i++) {
        for (int j = i + 1; j < stack.size(); j++) {
          if (var(stack.get(i)) == var(stack.get(j))) {
            throw new Exception(
                "Duplicate variable " + var(stack.get(i))
                + " in learned clause " + stack + " in " + learned);
          }
        }
      }
    } else {
      while (learned.get(p) != 0) {
        p = verifyLearned(p, stack);
        if (p >= learned.size()) {
          throw new Exception("Unexpected end of tree at " + p
                              + " in " + learned);
        }
      }
    }

    stack.removeAt(stack.size() - 1);
    return p + 1;
  }

  /** Adds learned clauses to formula. */
  public void addLearnedClauses(final TIntArrayList formula, final int limit) {
    addLearnedClauses(formula, 0, new TIntArrayList(), limit);
  }

  /** Expands the tree of learned clauses and adds them to formula. */
  private int addLearnedClauses(final TIntArrayList formula, 
                                final int start,
                                final TIntArrayList stack,
                                final int limit) {
    int p = start;
    if (p == learned.size()) {
      return p;
    }

    if (learned.get(p) == 0) {
      if (stack.size() <= limit) {
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
      p = addLearnedClauses(formula, p + 1, stack, limit);
      stack.removeAt(stack.size() - 1);
    }

    return learned.size();
  }
}
