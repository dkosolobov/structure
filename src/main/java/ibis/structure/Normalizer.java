package ibis.structure;

import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class Normalizer {
  private static final Logger logger = Logger.getLogger(Normalizer.class);

  private int oldNumVariables = 0;
  /** Maps old names to new names. */
  private TIntIntHashMap variableMap = null;
  /** Maps new names to old names. */
  private TIntIntHashMap inverseMap = null;

  /** Normalizes given instance. */
  public void normalize(final Skeleton instance) {
    oldNumVariables = instance.numVariables;
    variableMap = new TIntIntHashMap();
    inverseMap = new TIntIntHashMap();

    // Normalizes formula.
    TIntArrayList formula = instance.formula;
    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);

      for (int i = clause; i < clause + length; i++) {
        int literal = formula.getQuick(i);
        int renamed = rename(literal);
        formula.set(i, renamed);
      }
    }
    instance.numVariables = variableMap.size() / 2;
  }

  /** Denormalizes in place a Core. */
  public Core denormalize(final Core core) {
    denormalize(core.units());
    denormalize(core.proxies());

    // Denormalizes formula.
    TIntArrayList formula = core.instance().formula;
    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);

      for (int i = clause; i < clause + length; i++) {
        int literal = formula.getQuick(i);
        int renamed = inverseMap.get(literal);
        formula.setQuick(i, renamed);
      }
    }
    core.instance().numVariables = oldNumVariables;

    return core;
  }

  /** Denormalizes in place a solution. */
  public Solution denormalize(final Solution solution) {
    if (solution.isSatisfiable()) {
      denormalize(solution.units());
    } else {
      denormalize(solution.learned());
    }
    return solution;
  }

  /** Denormalizes in place an array of literals. */
  private void denormalize(final TIntArrayList array) {
    for (int i = 0; i < array.size(); ++i) {
      array.set(i, inverseMap.get(array.get(i)));
    }
  }

  /** Renames a literal under current variable map. */
  private int rename(final int literal) {
    int rename = variableMap.get(literal);
    if (rename == 0) {
      rename = variableMap.size() / 2 + 1;
      if (literal < 0) {
        // Keeps the same sign for literal to preserve XOR clauses.
        rename = neg(rename);
      }

      variableMap.put(literal, rename);
      variableMap.put(neg(literal), neg(rename));

      inverseMap.put(rename, literal);
      inverseMap.put(neg(rename), neg(literal));
    }

    return rename;
  }
}
