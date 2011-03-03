package ibis.structure;

import gnu.trove.list.array.TIntArrayList;

import static ibis.structure.Misc.*;


public final class Core {
  private int numVariables;
  /** Units. */
  private BitSet units = new BitSet();
  /** Proxies for equivalent literals. */
  private int[] proxies;
  /** Core instance without units and equivalent literals. */
  private Skeleton instance;

  public Core(final int numVariables,
              final int[] units,
              final int[] proxies,
              final TIntArrayList formula) {
    this.numVariables = numVariables;
    this.units.addAll(units);
    this.proxies = proxies;

    assert !formula.isEmpty();
    instance = new Skeleton(numVariables);
    instance.formula = formula;
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
    assert solution.isSatisfiable();
    // Adds instance's units
    units.addAll(solution.units());

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

    Solution result = Solution.satisfiable(units.elements());

    // this Core becomes unusable
    units = null;
    proxies = null;
    instance = null;

    return result;
  }
}
