package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;

import static ibis.structure.Misc.*;


public final class Core {
  private final int numVariables;
  private TIntHashSet units = new TIntHashSet();
  /** Proxies for equivalent literals. */
  private int[] proxies;
  /** Variable elimination. */
  private Object ve;
  /** Core instance without units and equivalent literals. */
  private Skeleton instance;

  public Core(final int numVariables,
              final int[] units,
              final int[] proxies,
              final Object ve,
              final TIntArrayList formula) {
    this.numVariables = numVariables;
    this.units.addAll(units);
    this.proxies = proxies;
    this.ve = ve;

    assert !formula.isEmpty();
    instance = new Skeleton(numVariables, formula);
  }

  /** Returns core's instance. */
  public Skeleton instance() {
    return instance;
  }

  /** Frees any memory unnecessary to merge solution. */
  public void gc() {
    instance = null;
  }

  /** Merges solution. */
  public Solution merge(final Solution solution) {
    if (!solution.isSatisfiable()) {
      return solution;
    }

    // System.err.println("merging  ***************");
    // System.err.println("old units " + units);

    // Adds instance's units
    units.addAll(solution.units());
    // System.err.println("new units " + units);

    // Adds equivalent literals
    // XXX A bug prevents from iterating from -numVariables to +numVariables
    for (int literal = 1; literal <= numVariables; ++literal) {
      if (literal != proxies[literal + numVariables]) {
        // System.err.println(literal + " <- " + proxies[literal + numVariables]);

        if (units.contains(proxies[literal + numVariables])) {
          units.add(literal);
        } else if (units.contains(proxies[-literal + numVariables])) {
          units.add(-literal);
        }
      }
    }

    Solution tmp = Solution.satisfiable(units.toArray());
    return VariableElimination.restore(ve, tmp);
  }
}
