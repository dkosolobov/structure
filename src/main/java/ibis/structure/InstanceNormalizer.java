package ibis.structure;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntIntHashMap;;
import org.apache.log4j.Logger;


public final class InstanceNormalizer {
  private static final Logger logger = Logger.getLogger(InstanceNormalizer.class);

  private TIntIntHashMap variableMap = new TIntIntHashMap();

  /** Normalizes given instance. */
  public void normalize(final Skeleton instance) {
    TIntArrayList clauses = instance.clauses;
    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.get(i);
      if (!variableMap.contains(literal)) {
        int renamed = variableMap.size() / 2 + 1;
        variableMap.put(literal, renamed);
        variableMap.put(-literal, -renamed);
      }
    }
    variableMap.put(0, 0);  // for convenience

    instance.numVariables = variableMap.size() / 2;
    for (int i = 0; i < clauses.size(); ++i) {
      clauses.set(i, variableMap.get(clauses.get(i)));
    }
  }

  /** Denormalizes inplace an array of literals. */
  public void denormalize(final Solution solution) {
    // Builds the inverse of variableMap.
    TIntIntHashMap inverseMap = new TIntIntHashMap();
    TIntIntIterator it = variableMap.iterator();
    for (int size = variableMap.size(); size > 0; size--) {
      it.advance();
      inverseMap.put(it.value(), it.key());
    }

    int[] array = solution.units();
    for (int i = 0; i < array.length; ++i) {
      array[i] = inverseMap.get(array[i]);
    }
  }
}
