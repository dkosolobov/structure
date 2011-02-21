package ibis.structure;

import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntArrayList;

import static ibis.structure.Misc.*;


public final class Core {
  private int numVariables;
  /** Units. */
  private BitSet units = new BitSet();
  /** Proxies for equivalent literals */
  private int[] proxies;
  private TIntArrayList dvClauses;
  /** Core instance without units and equivalent literals */
  private Skeleton instance;

  public Core(final int numVariables, final int[] units,
              final int[] proxies, final TIntArrayList dvClauses,
              final Skeleton instance) {
    assert !instance.formula.isEmpty();
    this.numVariables = numVariables;
    this.units.addAll(units);
    this.proxies = proxies;
    this.dvClauses = dvClauses;
    this.instance = instance;
  }

  /** Returns core's instance. */
  public Skeleton instance() {
    return instance;
  }

  /** Merges solution. */
  public Solution merge(final Solution solution) {
    assert solution.isSatisfiable();

    // Adds instance's units
    units.addAll(solution.units());

    // Adds equivalent literals
    for (int literal = 1; literal <= numVariables; ++literal) {
      if (literal != proxies[literal + numVariables]) {
        if (units.contains(proxies[literal + numVariables])) {
          units.add(literal);
        } else if (units.contains(proxies[-literal + numVariables])) {
          units.add(-literal);
        }
      }
    }

    if (dvClauses != null) {
      DependentVariableElimination.addUnits(dvClauses, units);
    }

    Solution result = Solution.satisfiable(units.elements());

    // this Core becomes unusable
    units = null;
    proxies = null;
    instance = null;

    return result;
  }
}
