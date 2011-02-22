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
  /** Clauses from DVE and BCE. */
  private final TIntArrayList dve, bce;
  /** Core instance without units and equivalent literals */
  private Skeleton instance;

  public Core(final int numVariables,
              final int[] units,
              final int[] proxies,
              final TIntArrayList dve,
              final TIntArrayList bce,
              final Skeleton instance) {
    assert !instance.formula.isEmpty();
    this.numVariables = numVariables;
    this.units.addAll(units);
    this.proxies = proxies;
    this.dve = dve;
    this.bce = bce;
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
    if (dve != null) {
      BlockedClauseElimination.addUnits(bce, units);
    }

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

    if (dve != null) {
      DependentVariableElimination.addUnits(dve, units);
    }
    Solution result = Solution.satisfiable(units.elements());

    // this Core becomes unusable
    units = null;
    proxies = null;
    instance = null;

    return result;
  }
}
