package ibis.structure;

import java.util.Vector;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

public class VariableElimination {
  private static final Logger logger = Logger.getLogger(VariableElimination.class);

  /** Stores information how to compute value of literal. */
  private static class Data {
    public int literal;
    public TIntArrayList clauses;

    public Data(final int literal, final TIntArrayList clauses) {
      this.literal = literal;
      this.clauses = clauses;
    }
  }

  private final Solver solver;
  private final TouchSet touched;
  /** Vector of eliminated variables and information to compute their value. */
  private final Vector<Data> eliminated;

  private VariableElimination(final Solver solver) {
    this.solver = solver;
    touched = new TouchSet(solver.numVariables);
    eliminated = new Vector<Data>();
  }

  public static Object run(final Solver solver) throws ContradictionException {
    return (new VariableElimination(solver)).run();
  }

  /** Computes values of eliminated variables. */
  public static Solution restore(final Object ve_, final Solution solution) {
    if (!solution.isSatisfiable() || ve_ == null) {
      return solution;
    }

    Vector<Data> ve = (Vector<Data>) ve_;
    TIntHashSet units = new TIntHashSet(solution.units());

    for (int i = ve.size() - 1; i >= 0; i--) {
      Data data = ve.get(i);
      units.remove(neg(data.literal));
      units.add(data.literal);

      ClauseIterator it = new ClauseIterator(data.clauses);
      while (it.hasNext()) {
        int clause = it.next();
        int length = length(data.clauses, clause);

        for (int j = clause; j < clause + length; j++) {
          int u = data.clauses.getQuick(j);
          if (!units.contains(neg(u))) {  // Literal may be missing.
            units.add(u);
          }
        }

        if (!isClauseSatisfied(data.clauses, clause, units)) {
          units.remove(data.literal);
          units.add(neg(data.literal));
          assert isClauseSatisfied(data.clauses, clause, units);
        }
      }
    }

    return Solution.satisfiable(units);
  }

  /**
   * Runs VariableElimination on a given instance.
   *
   * @return an object storing information to compute
   * the values of the original instance.
   */
  private Object run() throws ContradictionException {
    run(2);
    run(0);
    run(-1);

    SelfSubsumming.run(solver);
    return eliminated;
  }

  private void run(final int limit)
      throws ContradictionException {
    solver.propagateBinaries();
    HiddenTautologyElimination.run(solver);
    SelfSubsumming.run(solver);

    for (int literal = 1; literal <= solver.numVariables; literal++) {
      eliminate(literal, limit);
    }
  }

  /**
   * Attempts to eliminate a literal such that the formula
   * doesn't increase more than limit.
   */
  private void eliminate(final int literal, final int limit)
      throws ContradictionException {
    assert !solver.isLiteralAssigned(literal);

    int[] p = solver.watchLists.get(literal).toArray();
    int[] n = solver.watchLists.get(neg(literal)).toArray();
    int size = 0;

    if (p.length == 0 && n.length == 0) {
      return;
    }

    // Clauses must be leng enough and not XORs.
    for (int i = 0; i < p.length; i++) {
      size += length(solver.formula, p[i]) + 1;
      if (type(solver.formula, p[i]) != OR) {
        return;
      }
      if (length(solver.formula, p[i]) == 1) {
        return;
      }
    }
    for (int i = 0; i < n.length; i++) {
      size += length(solver.formula, n[i]) + 1;
      if (type(solver.formula, n[i]) != OR) {
        return;
      }
      if (length(solver.formula, n[i]) == 1) {
        return;
      }
    }

    TIntArrayList store = new TIntArrayList();
    for (int i = 0; i < p.length; i++) {
      for (int j = 0; j < n.length; j++) {
        int length = resolution(store, p[i], n[j], literal);
        if (length > 8 || store.size() >= size + limit) {
          return;
        }
      }
    }

    TIntArrayList clauses = removeLiteral(literal, p, n);
    solver.watchLists.append(store);
    eliminated.add(new Data(literal, clauses));
  }

  /**
   * Performs resolution between first and second on literal and
   * puts the resulted clause in store.
   */
  private int resolution(final TIntArrayList store,
                         final int first,
                         final int second,
                         final int literal) {
    store.add(0);
    int storeClause = store.size();

    // Adds literals from the first storeClause.
    int length = length(solver.formula, first);
    for (int i = first; i < first + length; i++) {
      int u = solver.formula.getQuick(i);
      if (u != literal) {
        store.add(u);
      }
    }

    // Adds literals from the second storeClause.
    length = length(solver.formula, second);
    for (int i = second; i < second + length; i++) {
      int u = solver.formula.getQuick(i);
      if (u != neg(literal)) {
        store.add(u);
      }
    }

    // Sets the length and type.
    length = store.size() - storeClause;
    store.setQuick(storeClause - 1, encode(length, OR));
    return cleanLastClause(store, storeClause);
  }

  /**
   * Cleans last clauses added to store, removing it
   * if the clause is a tautology.
   */
  private int cleanLastClause(final TIntArrayList store,
                               final int clause) {
    touched.reset();

    int length = length(store, clause);
    int p = clause;
    for (int i = clause; i < clause + length; i++) {
      int u = store.getQuick(i);
      if (touched.contains(neg(u))) {
        store.remove(clause - 1, store.size() - clause + 1);
        return 0;
      } else if (!touched.containsOrAdd(u)) {
        store.setQuick(p, u);
        p++;
      }
    }

    store.remove(p, store.size() - p);
    store.setQuick(clause - 1, encode(p - clause, OR));
    return p - clause;
  }

  /**
   * Removes literal from clauses in p and n
   *
   * @param literal literal to be removed
   * @param p clauses containing literal
   * @param n clauses containing -literal
   * @return formula with removed clauses
   */
  private TIntArrayList removeLiteral(final int literal,
                                      final int[] p,
                                      final int[] n) {
    TIntArrayList clauses = new TIntArrayList();

    for (int i = 0; i < p.length; i++) {
      copy(clauses, solver.formula, p[i]);
      solver.watchLists.removeClause(p[i]);
    }

    for (int i = 0; i < n.length; i++) {
      copy(clauses, solver.formula, n[i]);
      solver.watchLists.removeClause(n[i]);
    }

    return clauses;
  }
}
