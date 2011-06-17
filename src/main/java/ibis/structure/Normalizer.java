package ibis.structure;

import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class Normalizer {
  private static final Logger logger = Logger.getLogger(Normalizer.class);

  private TIntIntHashMap variableMap = new TIntIntHashMap();

  /** Normalizes given instance. */
  public void normalize(final TDoubleArrayList scores,
                        final Skeleton instance) {
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

    // TODO: Inefficient
    // 1. inverseMap can be built here
    // 2. scores.clear() / scores.add()
  
    if (scores != null) {
      double[] newScores = new double[instance.numVariables + 1];
      TIntIntIterator it1 = variableMap.iterator();
      for (int size = variableMap.size(); size > 0; size--) {
        it1.advance();
        newScores[var(it1.value())] = scores.getQuick(var(it1.key()));
      }

      scores.clear();
      scores.add(newScores);
    }
  }

  /** Renames a literal under current variable map. */
  public int rename(final int literal) {
    int rename = variableMap.get(literal);
    if (rename == 0) {
      rename = variableMap.size() / 2 + 1;
      if (literal < 0) {
        rename = neg(rename);
      }

      variableMap.put(literal, rename);
      variableMap.put(neg(literal), neg(rename));
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

    if (solution.isSatisfiable()) {
      int[] array = solution.units();
      for (int i = 0; i < array.length; ++i) {
        array[i] = inverseMap.get(array[i]);
      }
    } else {
      TIntArrayList array = solution.learned();
      for (int i = 0; i < array.size(); ++i) {
        array.set(i, inverseMap.get(array.get(i)));
      }
    }
  }
}
