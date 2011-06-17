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
  /** Number of variables. */
  private int numVariables;
  /** Set of units. */
  private TIntHashSet units = new TIntHashSet();
  /** Proxies for equivalent literals. */
  private int[] proxies;
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
              final int[] units,
              final int[] proxies,
              final TIntArrayList formula) {
    this.numVariables = numVariables;
    this.units.addAll(units);
    this.proxies = proxies;
    this.instance = new Skeleton(numVariables, formula);
  }

  /** Returns number of variables. */
  public int numVariables() {
    return numVariables;
  }

  /** Returns units. */
  public TIntHashSet units() {
    return units;
  }

  /** Returns proxies. */
  public int[] proxies() {
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

  /** Merges solution. */
  public Solution merge(final Solution solution) {
    if (!solution.isSatisfiable()) {
      return solution;
    }

    // Adds instance's units
    units.addAll(solution.units());

    // Adds equivalent literals
    // XXX A bug prevents from iterating from -numVariables to +numVariables
    for (int literal = 1; literal <= numVariables; ++literal) {
      if (literal != proxies[literal + numVariables]) {
        if (units.contains(proxies[literal + numVariables])) {
          units.add(literal);
        } else if (units.contains(proxies[neg(literal) + numVariables])) {
          units.add(neg(literal));
        }
      }
    }

    return Solution.satisfiable(units.toArray());
  }
}
