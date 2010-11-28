package ibis.structure;

public final class Solution {
  public static final int SATISFIABLE = 10;
  public static final int UNSATISFIABLE = 20;
  public static final int UNKNOWN = 30;

  private int solved = UNKNOWN;  // SATISFIABLE, UNSATISFIABLE or UNKNOWN
  private int[] variableMap = null;
  private int[] units = null;
  private int[] proxies = null;
  private Skeleton core = null;
  private int branch = 0;

  public static Solution unsatisfiable() {
    Solution solution = new Solution(UNSATISFIABLE);
    return solution;
  }

  public static Solution satisfiable(int[] variableMap, int[] units,
                                     int[] proxies) {
    Solution solution = new Solution(SATISFIABLE);
    solution.variableMap = variableMap;
    solution.units = units;
    solution.proxies = proxies;
    return solution;
  }

  public static Solution unknown(int[] variableMap, int[] units, int[] proxies,
                                 Skeleton core, int branch) {
    Solution solution = new Solution(UNKNOWN);
    solution.variableMap = variableMap;
    solution.units = units;
    solution.proxies = proxies;
    solution.core = core;
    solution.branch = branch;
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

  public int[] solution() {
    assert solved == SATISFIABLE;
    return solution(null);
  }

  public int[] solution(int[] coreSolution) {
    assert solved != UNSATISFIABLE;
    BitSet all = new BitSet();

    // Adds units and core's solution
    all.addAll(units);
    if (!isSatisfiable()) {
      assert solved == UNKNOWN && coreSolution != null;
      all.addAll(coreSolution);
    }

    // Adds equivalent literals
    for (int literal = 1; literal < proxies.length; ++literal) {
      if (literal != proxies[literal]) {
        if (all.contains(proxies[literal])) {
          all.add(literal);
        } else if (all.contains(-proxies[literal])) {
          all.add(-proxies[literal]);
        }
      }
    }

    // Denormalizes all literals
    int[] solution = all.elements();
    for (int i = 0; i < solution.length; ++i) {
      if (solution[i] < 0) {
        solution[i] = -variableMap[-solution[i]];
      } else if (solution[i] > 0) {
        solution[i] = variableMap[solution[i]];
      }
    }

    return solution;
  }
}
