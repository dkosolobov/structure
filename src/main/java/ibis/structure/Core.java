package ibis.structure;

import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntArrayList;


public final class Core {
  /** Units. */
  private BitSet units = new BitSet();
  /** Proxies for equivalent literals */
  private int[] proxies = null;
  /** Core instance without units and equivalent literals */
  private Skeleton instance = null;
  /** The variable map for (de)normalization. */
  private TIntIntHashMap variableMap = null;

  public Core(final int[] units, final int[] proxies, final Skeleton instance) {
    assert !instance.clauses.isEmpty();

    this.units.addAll(units);
    this.proxies = proxies;
    this.instance = instance;

    buildVariableMap();
    normalizeCore();
  }

  public Skeleton instance() {
    return instance;
  }

  /** Merges solution. */
  public Solution merge(final Solution solution) {
    assert solution.isSatisfiable();

    // Adds instance's units
    int[] newUnits = solution.units();
    newUnits = java.util.Arrays.copyOf(newUnits, newUnits.length);
    denormalize(newUnits);
    units.addAll(newUnits);

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
    variableMap = null;
    proxies = null;
    instance = null;

    return result;
  }

  /** Builds the variable map. */
  private void buildVariableMap() {
    variableMap = new TIntIntHashMap();
    for (int i = 0; i < instance.clauses.size(); ++i) {
      int literal = instance.clauses.get(i);
      if (!variableMap.contains(literal)) {
        int renamed = (variableMap.size() / 2) + 1;
        variableMap.put(literal, renamed);
        variableMap.put(-literal, -renamed);
      }
    }
    variableMap.put(0, 0);  // for convenience
  }

  /** Normalizes instance instance. */
  private void normalizeCore() {
    instance.numVariables = variableMap.size() / 2;
    for (int i = 0; i < instance.clauses.size(); ++i) {
      instance.clauses.set(i, normalize(instance.clauses.get(i)));
    }
  }

  /** Normalizes a single literal */
  public int normalize(int literal) {
    return variableMap.get(literal);
  }

  /** Denormalizes inplace an array of literals. */
  private void denormalize(final int[] array) {
    // variableMap is the inverse of inverseMap
    TIntIntHashMap inverseMap = new TIntIntHashMap();
    TIntIntIterator it = variableMap.iterator();
    for (int size = variableMap.size(); size > 0; size--) {
      it.advance();
      inverseMap.put(it.value(), it.key());
    }

    for (int i = 0; i < array.length; ++i) {
      array[i] = inverseMap.get(array[i]);
    }
  }
}
