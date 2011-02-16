package ibis.structure;

import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntArrayList;


public final class Core {
  /** Units. */
  public BitSet units = new BitSet();
  /** Proxies for equivalent literals */
  public int[] proxies = null;
  /** Core instance without units and equivalent literals */
  private Skeleton instance = null;

  public Core(final int[] units, final int[] proxies, final Skeleton instance) {
    assert !instance.formula.isEmpty();
    this.units.addAll(units);
    this.proxies = proxies;
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
    for (int literal = 1; literal < proxies.length; ++literal) {
      if (literal != proxies[literal]) {
        if (units.contains(proxies[literal])) {
          units.add(literal);
        } else if (units.contains(-proxies[literal])) {
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
