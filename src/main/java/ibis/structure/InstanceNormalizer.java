package ibis.structure;

import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class InstanceNormalizer {
  private static final Logger logger = Logger.getLogger(InstanceNormalizer.class);

  private TIntIntHashMap variableMap = new TIntIntHashMap();

  /** Normalizes given instance. */
  public void normalize(final Skeleton instance) {
    TIntArrayList formula = instance.formula;

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int type = type(formula, clause);
      int negative = 0;

      for (int i = clause; i < clause + length; i++) {
        int literal = formula.get(i);
        int renamed = rename(literal);

        if (type == OR) {
          formula.set(i, renamed);
        } else {
          assert type == NXOR || type == XOR;
          formula.set(i, var(renamed));
          negative += renamed > 0 ? 0 : 1;
        }
      }

      if (type == XOR || type == NXOR) {
        if ((negative & 1) != 0) {
          switchXOR(formula, clause);
        }
      }
    }

    instance.numVariables = variableMap.size() / 2;
  }

  private int rename(final int literal) {
    int rename = variableMap.get(literal);
    if (rename == 0) {
      rename = variableMap.size() / 2 + 1;
      variableMap.put(literal, rename);
      variableMap.put(-literal, -rename);
    }

    return rename;
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
