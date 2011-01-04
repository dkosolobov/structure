package ibis.structure;

import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;


public final class Solution {
  public static final int SATISFIABLE = 10;
  public static final int UNSATISFIABLE = 20;
  public static final int UNKNOWN = 30;

  private int solved = UNKNOWN;  // SATISFIABLE, UNSATISFIABLE or UNKNOWN
  private TIntIntHashMap variableMap = null;
  private int[] units = null;
  private int[] proxies = null;
  private Skeleton core = null;
  private int branch = 0;

  public static Solution unsatisfiable() {
    Solution solution = new Solution(UNSATISFIABLE);
    return solution;
  }

  public static Solution satisfiable(int[] units, int[] proxies) {
    Solution solution = new Solution(SATISFIABLE);
    solution.units = units;
    solution.proxies = proxies;
    return solution;
  }

  public static Solution unknown(int[] units, int[] proxies, Skeleton core, int branch) {
    Solution solution = new Solution(UNKNOWN);
    solution.units = units;
    solution.proxies = proxies;
    solution.core = core;
    solution.branch = branch;
    solution.normalize();
    return solution;
  }

  private Solution(int solved) {
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
   */
  public int[] solution() {
    assert solved == SATISFIABLE;
    return solution(null);
  }

  /**
   * Returns the solution as an array of units.
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
