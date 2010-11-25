package ibis.structure;

public final class Solution {
  private int[] variableMap;
  private int[] units;
  private int[] proxies;
  private Skeleton core;
  private int branch;

  public Solution(int[] variableMap, int[] units,
                  int[] proxies, Skeleton core, int branch) {
    this.variableMap = variableMap;
    this.units = units;
    this.proxies = proxies;
    this.core = core;
    this.branch = branch;
  }

  public boolean satisfied() {
    return core == null;
  }

  public Skeleton core() {
    return core;
  }

  public int branch() {
    return branch;
  }

  public int[] solution(int[] coreSolution) {
    BitSet all = new BitSet();

    // Adds units and core's solution
    all.addAll(units);
    if (!satisfied()) {
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
