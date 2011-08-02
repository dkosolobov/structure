package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;

import static ibis.structure.Misc.*;


/**
 * Represents a core instance after simplifications.
 *
 * It contains a smaller formula to needed to be solved
 * in order to solve the original formula.
 */
public final class Core {
  /** Set of units. */
  private TIntArrayList units;
  /** Proxies for equivalent literals. */
  private TIntArrayList proxies;
  /** Core instance without units and equivalent literals. */
  private Skeleton instance;

  /**
   * Constructor.
   *
   * @param numVariables number of variables
   * @param units units discovered
   * @param proxies equivalent literals
   * @param formula smaller formula needed to be solved.
   */
  public Core(final int numVariables,
              final TIntArrayList units,
              final TIntArrayList proxies,
              final TIntArrayList formula) {
    this.units = units;
    this.proxies = proxies;
    this.instance = new Skeleton(numVariables, formula);
  }

  /** Returns number of variables. */
  public int numVariables() {
    return instance.numVariables;
  }

  /** Returns units. */
  public TIntArrayList units() {
    return units;
  }

  /** Returns proxies. */
  public TIntArrayList proxies() {
    return proxies;
  }

  /** Returns core's instance. */
  public Skeleton instance() {
    return instance;
  }

  /** Frees any memory unnecessary to merge solution. */
  public void gc() {
    if (!Configure.enableExpensiveChecks) {
      instance = null;
    }
  }

  /** Merges satisfiable solution. */
  public Solution merge(final Solution solution) {
    assert solution.isSatisfiable();

    TIntHashSet merged = new TIntHashSet();
    merged.addAll(units);
    merged.addAll(solution.units());

    for (int i = 0; i < proxies.size(); i += 2) {
      int u = proxies.getQuick(i);
      int v = proxies.getQuick(i + 1);

      assert merged.contains(v) || merged.contains(neg(v))
          : "Proxy " + v + " not found";
      assert !merged.contains(v) || !merged.contains(neg(v));
      assert !merged.contains(u) && !merged.contains(neg(u))
          : "Original literal " + u + " should not be already assigned";
      merged.add(merged.contains(v) ? u : neg(u));
    }

    return Solution.satisfiable(merged);
  }
}
