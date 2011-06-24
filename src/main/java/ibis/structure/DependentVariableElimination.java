package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.iterator.TIntIterator;;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/**
 * Performs Dependent Variable Elimination.
 *
 * A variable is dependent if it appears only in XOR gates.
 */
public final class DependentVariableElimination {
  private static final Logger logger = Logger.getLogger(
      DependentVariableElimination.class);

  /**
   * Performs dependent variable elimination on formula with xorGates.
   */
  public static TIntArrayList run(final int numVariables,
                                  final TIntArrayList formula,
                                  final TIntArrayList xorGates) {
    TIntHashSet independent = findIndependentVariables(formula);
    TIntObjectHashMap<TIntHashSet> wl = buildWatchList(xorGates);
    // dve is build in reverse order.
    TIntArrayList dve = new TIntArrayList();
    int numDependent = 0;

    for (int literal = 1; literal <= numVariables; literal++) {
      if (independent.contains(literal)) {
        continue;
      }

      TIntHashSet clauses = wl.get(literal);
      if (clauses == null || clauses.isEmpty()) {
        continue;
      }

      numDependent++;
      int pivot = findShortestClause(xorGates, clauses);

      TIntIterator it = clauses.iterator();
      for (int size = clauses.size(); size > 0; size--) {
        int clause = it.next();
        if  (clause == pivot) {
          continue;
        }

        int newClause = sum(xorGates, pivot, clause);

        // Removes old clause from watch lists
        it.remove();
        int length = length(xorGates, clause);
        for (int i = clause; i < clause + length; i++) {
          wl.get(xorGates.getQuick(i)).remove(clause);
        }

        // Add the new clause to watch lists.
        length = length(xorGates, newClause);
        for (int i = newClause; i < newClause + length; i++) {
          wl.get(xorGates.getQuick(i)).add(newClause);
        }

        removeClause(xorGates, clause);
      }

      int length = length(xorGates, pivot);
      int type = type(xorGates, pivot);

      // Removes the clause with the dependent variable
      // and puts into dve.
      for (int i = pivot; i < pivot + length; i++) {
        int u = xorGates.getQuick(i);
        wl.get(u).remove(pivot);
        dve.add(u);
      }
      removeClause(xorGates, pivot);

      // Moves literal on last position
      int index = dve.lastIndexOf(literal);
      dve.setQuick(index, dve.getQuick(dve.size() - 1));
      dve.setQuick(dve.size() - 1, literal);

      dve.add(encode(length, type));
    }

    logger.info("Found " + numDependent + " dependent variables");
    dve.reverse();
    compact(xorGates);
    return dve;
  }

  /** Fixes units to satisfy clauses with dependent variables */
  public static Solution restore(final TIntArrayList dve,
                                 final Solution solution) {
    if (!solution.isSatisfiable() || dve == null || dve.isEmpty()) {
      return solution;
    }

    TIntHashSet units = new TIntHashSet(solution.units());
    ClauseIterator it = new ClauseIterator(dve);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(dve, clause);
      int type = type(dve, clause);

      // Literal might have been assigned because
      // after DVE it is missing from formula.
      int literal = dve.get(clause);
      units.remove(literal);
      units.remove(neg(literal));

      int xor = 0;
      for (int i = clause + 1; i < clause + length; i++) {
        int u = dve.getQuick(i);
        if (units.contains(u)) {
          xor ^= 1;
        }
      }

      if (type == XOR) {
        if (xor == 1) {
          units.add(neg(literal));
        } else {
          units.add(literal);
        }
      } else {
        assert type == NXOR;
        if (xor == 1) {
          units.add(literal);
        } else {
          units.add(neg(literal));
        }
      }
    }

    return Solution.satisfiable(units);
  }
  
  /** Returns a set with all independent variables. */
  private static TIntHashSet findIndependentVariables(
      final TIntArrayList formula) {
    TIntHashSet independent = new TIntHashSet();
    ClauseIterator it = new ClauseIterator(formula);

    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      assert type(formula, clause) == OR
          : "Expected only OR clauses but got " + clauseToString(formula, clause);

      for (int i = clause; i < clause + length; i++) {
        independent.add(var(formula.getQuick(i)));
      }
    }

    return independent;
  }

  private static TIntObjectHashMap<TIntHashSet> buildWatchList(
      final TIntArrayList xorGates) {
    TIntObjectHashMap<TIntHashSet> wl = new TIntObjectHashMap<TIntHashSet>();
    ClauseIterator it = new ClauseIterator(xorGates);

    while (it.hasNext()) {
      int clause = it.next();
      int length = length(xorGates, clause);

      for (int i = clause; i < clause + length; i++) {
        int literal = xorGates.getQuick(i);
        TIntHashSet clauses = wl.get(literal);
        if (clauses == null) {
          clauses = new TIntHashSet();
          wl.put(literal, clauses);
        }
        clauses.add(clause);
      }
    }

    return wl;
  }

  private static int findShortestClause(final TIntArrayList xorGates,
                                        final TIntHashSet clauses) {
    int bestClause = -1;
    int bestLength = 0;

    TIntIterator it = clauses.iterator();
    for (int size = clauses.size(); size > 0; size--) {
      int clause = it.next();
      int length = length(xorGates, clause);
      assert length >= 1;

      if (bestLength == 0 || length < bestLength) {
        bestClause = clause;
        bestLength = length;
      }
    }

    return bestClause;
  }

  /**
   * Adds the XOR clauses first and second, appends the
   * new clause to xorGates and returns it.
   */
  private static int sum(
      final TIntArrayList xorGates, final int first, final int second) {
    xorGates.add(0);
    int clause = xorGates.size();
    int length;

    length = length(xorGates, first);
    for (int i = first; i < first + length; i++) {
      xorGates.add(xorGates.getQuick(i));
    }

    length = length(xorGates, second);
    for (int i = second; i < second + length; i++) {
      xorGates.add(xorGates.getQuick(i));
    }

    length = length(xorGates, first) + length(xorGates, second);
    xorGates.setQuick(clause - 1, encode(length, OR));

    for (int i = clause; i < clause + length; i++) {
      int literal = xorGates.getQuick(i);
      int other = xorGates.indexOf(clause, literal);
      if (other != i) {
        xorGates.setQuick(i, 0);
        xorGates.setQuick(other, 0);
      }
    }

    int p = clause;
    for (int i = clause; i < clause + length; i++) {
      int literal = xorGates.getQuick(i);
      if (literal != 0) {
        xorGates.setQuick(p, literal);
        p++;
      }
    }

    xorGates.remove(p, xorGates.size() - p);
    int type = type(xorGates, first) ^ type(xorGates, second);
    xorGates.setQuick(clause - 1, encode(p - clause, type));
    return clause;
  }
}
