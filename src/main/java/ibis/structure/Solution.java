package ibis.structure;

import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;


/**
 * Solution represents the solution for an instance.
 *
 * There are three posibilities
 *
 * <ul>
 * <tt>SATISFIABLE</tt> A solution was found.
 * <tt>UNSATISFIABLE</tt> Instance is unsatisfiable.
 * <tt>UNKNOWN</tt> a solution was not found, more branching is required.
 * </ul>
 */
public final class Solution {
  public static final int SATISFIABLE = 10;
  public static final int UNSATISFIABLE = 20;
  public static final int UNKNOWN = 30;

  private int solved = UNKNOWN;  // SATISFIABLE, UNSATISFIABLE or UNKNOWN
  private TIntIntHashMap variableMap = null;
  /** A vector of units. */
  private int[] units = null;
  /** Proxies for equivalent literals */
  private int[] proxies = null;
  /** Core instance without units and equivalent literals */
  private Skeleton core = null;
  /** The branch */
  private int branch = 0;

  /**
   * Returns a solution representing an unsatifiable instance.
   */
  public static Solution unsatisfiable() {
    Solution solution = new Solution(UNSATISFIABLE);
    return solution;
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
    return solution;
  }

  /**
   * Returns a solution representing an unknown instance.
   *
   * Solution contains units, proxies, normalized core instance and branch.
   */
  public static Solution unknown(final int[] units, final int[] proxies,
                                 final Skeleton core, final int branch) {
    Solution solution = new Solution(UNKNOWN);
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

  public Skeleton core() {
    return core;
  }

  public int branch() {
    return branch;
  }

  /**
   * Returns the solution as an array of units.
   *
   * @return all units.
   */
  public int[] solution() {
    assert solved == SATISFIABLE;
    return solution(null);
  }

  /**
   * Returns the solution as an array of units.
   *
   * @return all units
   */
  public int[] solution(final int[] coreSolution) {
    assert solved != UNSATISFIABLE;
    BitSet all = new BitSet();

    // Adds units and core's solution
    all.addAll(units);
    if (!isSatisfiable()) {
      assert solved == UNKNOWN && coreSolution != null;
      denormalize(coreSolution);
      all.addAll(coreSolution);
    }

    // Satisfiy missing proxies
    for (int literal = 1; literal < proxies.length; ++literal) {
      if (literal == proxies[literal]) {
        if (!all.contains(literal) && !all.contains(-literal)) {
          all.add(literal);
        }
      }
    }

    // Adds equivalent literals
    for (int literal = 1; literal < proxies.length; ++literal) {
      if (literal != proxies[literal]) {
        if (all.contains(proxies[literal])) {
          all.add(literal);
        } else if (all.contains(-proxies[literal])) {
          all.add(-literal);
        }
      }
    }

    return all.elements();
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
   * Denormalizes an array of literals.
   */
  private void denormalize(final int[] array) {
    /* variableMap is the inverse of inverseMap */
    TIntIntHashMap inverseMap = new TIntIntHashMap();
    for (TIntIntIterator it = variableMap.iterator(); it.hasNext(); ) {
      it.advance();
      inverseMap.put(it.value(), it.key());
    }

    for (int i = 0; i < array.length; ++i) {
      array[i] = inverseMap.get(array[i]);
    }
  }
}
