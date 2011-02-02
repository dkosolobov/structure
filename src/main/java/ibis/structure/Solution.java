package ibis.structure;

import java.io.PrintStream;
import java.util.Arrays;

import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntArrayList;


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
  public static final int SATISFIABLE = 10;
  public static final int UNSATISFIABLE = 20;
  public static final int UNKNOWN = 30;
  public static final int BRANCH = 40;

  /** One of: SATISFIABLE, UNSATISFIABLE or UNKNOWN */
  private int solved = UNKNOWN;
  /** Units. */
  private int[] units = null;
  /** Learned tree */
  public TIntArrayList learned = new TIntArrayList();
  /** The variable map for denormalization. */
  transient private TIntIntHashMap variableMap = null;
  /** Proxies for equivalent literals */
  transient private int[] proxies = null;
  /** Core instance without units and equivalent literals */
  transient private Skeleton core = null;
  /** The branch */
  transient private int branch = 0;

  /**
   * Returns a solution representing an unsatifiable instance.
   */
  public static Solution unsatisfiable() {
    return new Solution(UNSATISFIABLE);
  }

  /**
   * Returns a solution representing a satifiable instance.
   *
   * Solution contains units and proxies.
   */
  public static Solution satisfiable(final int[] units, final int[] proxies) {
    Solution solution = new Solution(SATISFIABLE);
    solution.units = units;
    solution.proxies = proxies;
    solution.satisfy();
    return solution;
  }

  /**
   * Returns a solution representing an unsatisfiable instance.
   */
  public static Solution unknown() {
    return new Solution(UNKNOWN);
  }

  /**
   * Returns a solution representing a branching instance.
   *
   * Solution contains units, proxies, normalized core instance and branch.
   */
  public static Solution branch(final int[] units, final int[] proxies,
                                final Skeleton core, final int branch) {
    Solution solution = new Solution(BRANCH);
    solution.units = units;
    solution.proxies = proxies;
    solution.core = core;
    solution.branch = branch;
    solution.normalize();
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

  public boolean isBranch() {
    return solved == BRANCH;
  }

  public Skeleton core() {
    return core;
  }

  public int solved() {
    return solved;
  }

  public int branch() {
    return branch;
  }

  /**
   * Merges a core solution.
   */
  public void merge(Solution core) {
    assert isBranch(): "Solution must be a branch to merge";
    assert core.isSatisfiable(): "Core solution must be satisfiable to merge";

    // Adds units and core's solution
    BitSet all = new BitSet();
    all.addAll(units);
    if (core != null) {
      int numUnits = units.length;
      int numCoreUnits = core.units.length;

      units = Arrays.copyOf(units, numUnits + numCoreUnits);
      for (int i = 0; i < numCoreUnits; ++i) {
        units[i + numUnits] = core.units[i];
      }
      denormalize(units, numUnits, numUnits + numCoreUnits);
    }

    satisfy();
  }

  private void satisfy() {
    assert !isUnsatisfiable();

    // Satisfiy redundant literals
    BitSet all = new BitSet();
    all.addAll(units);
    for (int literal = 1; literal < proxies.length; ++literal) {
      if (literal == proxies[literal]) {
        if (!all.get(literal) && !all.get(-literal)) {
          all.add(literal);
        }
      }
    }

    // Adds equivalent literals
    for (int literal = 1; literal < proxies.length; ++literal) {
      if (literal != proxies[literal]) {
        if (all.get(proxies[literal])) {
          all.add(literal);
        } else if (all.get(-proxies[literal])) {
          all.add(-literal);
        }
      }
    }

    solved = SATISFIABLE;
    units = all.elements();
    variableMap = null;
    proxies = null;
    core = null;
    branch = 0;
  }

  /**
   * Prints solution to out.
   */
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

      default:
        assert false: "Unknown solved " + solved;
    }

    if (isSatisfiable()) {
      out.print("v");
      for (int i = 0; i < units.length; ++i) {
         out.print(" " + units[i]);
      }
      out.println(" 0");
    }
  }

  /**
   * Normalizes core instance and branch.
   */
  private void normalize() {
    variableMap = new TIntIntHashMap();
    for (int i = 0; i < core.clauses.size(); ++i) {
      int literal = core.clauses.get(i);
      if (literal != 0) {
        int newLiteralName;
        if (!variableMap.contains(literal)) {
          newLiteralName = (variableMap.size() / 2) + 1;
          variableMap.put(literal, newLiteralName);
          variableMap.put(-literal, -newLiteralName);
        } else {
          newLiteralName = variableMap.get(literal);
        }
        core.clauses.set(i, newLiteralName);
      }
    }

    branch = variableMap.get(branch);
    core.numVariables = variableMap.size() / 2;
  }

  /**
   * Denormalizes inplace an array of units.
   */
  public void denormalize(final int[] array, int start, int end) {
    // Constracts inverse of variableMap
    TIntIntHashMap inverseMap = new TIntIntHashMap();
    TIntIntIterator it = variableMap.iterator();
    for (int size = variableMap.size(); size > 0; size--) {
      it.advance();
      inverseMap.put(it.value(), it.key());
    }

    for (int i = start; i < end; ++i) {
      array[i] = inverseMap.get(array[i]);
    }
  }
}
