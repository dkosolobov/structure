package ibis.structure;

import java.util.Vector;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.hash.TIntIntHashMap;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

public class VariableElimination {
  private static final Logger logger = Logger.getLogger(VariableElimination.class);
  private static final int MAX_SCORE = 64;

  private static class Data {
    public int literal;
    public TIntArrayList clauses;
  }

  private final Solver solver;
  private final TouchSet touched;

  private VariableElimination(final Solver solver) {
    this.solver = solver;
    touched = new TouchSet(solver.numVariables);
  }

  public static Object run(final Solver solver) throws ContradictionException {
    return (new VariableElimination(solver)).run();
  }

  /**
   * Computes the value of eliminated variables.
   */
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
        if (!isClauseSatisfied(data.clauses, clause, units)) {
          units.remove(data.literal);
          units.add(neg(data.literal));
          assert isClauseSatisfied(data.clauses, clause, units);
        }
      }
    }

    return Solution.satisfiable(units.toArray());
  }

  /**
   * Runs VariableElimination on a given instance.
   *
   * @return an object storing information to compute
   * the values of the original instance.
   */
  private Object run() throws ContradictionException {
    Vector<Data> ve = new Vector<Data>();

    TLongArrayList all = new TLongArrayList();
    for (int literal = 1; literal <= solver.numVariables; literal++) {
      int score = score(literal);
      if (0 < score && score <= MAX_SCORE) {
        all.add((((long) score) << 32) + literal);
      }
    }

    all.sort();
    for (int j = 0; j < all.size(); j++) {
      int literal = (int) all.get(j);
      Data data = eliminate(literal);
      if (data != null) {
        ve.add(data);
      }
    }

    logger.info("Eliminated " + ve.size() + " variables");
    return ve;
  }

  /**
   * Scores a given literal.
   */
  private int score(int literal) {
    return solver.numClauses(literal) * solver.numClauses(neg(literal));
  }

  private Data eliminate(final int literal)
      throws ContradictionException {
    // Checks that variable is suitable for elimination.
    if (solver.isLiteralAssigned(literal) || score(literal) > MAX_SCORE) {
      return null;
    }

    // Clauses must be short enough and not XORs.
    int[] p = solver.watchLists.get(literal).toArray();
    int[] n = solver.watchLists.get(neg(literal)).toArray();
    for (int i = 0; i < p.length; i++) {
      if (length(solver.formula, p[i]) > 16
          || type(solver.formula, p[i]) != OR) {
        return null;
      }
    }
    for (int i = 0; i < n.length; i++) {
      if (length(solver.formula, n[i]) > 16
          || type(solver.formula, n[i]) != OR) {
        return null;
      }
    }

    TIntArrayList store = resolution(literal, p, n);
    solver.watchLists.append(store);

    Data data = new Data();
    data.literal = literal;
    data.clauses = removeLiteral(literal, p, n);

    return data;
  }

  private TIntArrayList removeLiteral(final int literal, int[] p, int[] n) {
    TIntArrayList clauses = new TIntArrayList();

    for (int i = 0; i < p.length; i++) {
      copy(clauses, solver.formula, p[i]);
      solver.watchLists.removeClause(p[i]);
    }
    for (int i = 0; i < n.length; i++) {
      copy(clauses, solver.formula, n[i]);
      solver.watchLists.removeClause(n[i]);
    }

    TIntArrayList edges;
    edges = solver.graph.edges(literal);
    for (int j = 0; j < edges.size(); j++) {
      clauses.add(encode(2, OR));
      clauses.add(neg(literal));
      clauses.add(edges.getQuick(j));
    }

    edges = solver.graph.edges(neg(literal));
    for (int j = 0; j < edges.size(); j++) {
      clauses.add(encode(2, OR));
      clauses.add(literal);
      clauses.add(edges.getQuick(j));
    }

    solver.graph.remove(literal);

    return clauses;
  }

  private TIntArrayList resolution(final int literal, int[] p, int[] n) {
    TIntArrayList store = new TIntArrayList();

    // clause - clause
    for (int i = 0; i < p.length; i++) {
      for (int j = 0; j < n.length; j++) {
        resolution(store, p[i], n[j], literal);
      }
    }

    // clause - binary
    TIntArrayList pEdges = solver.graph.edges(literal);
    for (int i = 0; i < p.length; i++) {
      for (int j = 0; j < pEdges.size(); j++) {
        binaryResolution(store, p[i], neg(literal), pEdges.getQuick(j));
      }
    }

    // binary - clause
    TIntArrayList nEdges = solver.graph.edges(neg(literal));
    for (int i = 0; i < n.length; i++) {
      for (int j = 0; j < nEdges.size(); j++) {
        binaryResolution(store, n[i], literal, nEdges.getQuick(j));
      }
    }

    // binary - binary
    for (int i = 0; i < pEdges.size(); i++) {
      for (int j = 0; j < nEdges.size(); j++) {
        int u = pEdges.getQuick(i);
        int v = nEdges.getQuick(j);

        if (u == v) {
          store.add(encode(1, OR));
          store.add(u);
        } else if (u != neg(v)) {
          store.add(encode(2, OR));
          store.add(u);
          store.add(v);
        }
      }
    }

    return store;
  }

  /**
   * Performs resolution between first and second on literal and
   * puts the resulted clause in store.
   */
  private void resolution(final TIntArrayList store,
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
    cleanLastClause(store, storeClause);
  }

  /**
   * Perform a resolution between clause
   * and binary first + second and puts the resulted
   * clause in store.
   */
  private void binaryResolution(final TIntArrayList store,
                                final int clause,
                                final int first,
                                final int second) {
    if (first == neg(second)) {
      // Ignores binary tautologies.
      return;
    }

    store.add(0);
    int storeClause = store.size();

    // Adds literals from the first clause.
    int length = length(solver.formula, clause);
    for (int i = clause; i < clause + length; i++) {
      int u = solver.formula.getQuick(i);
      if (u == neg(first)) {
        if (first != second) {
          store.add(second);
        }
      } else {
        store.add(u);
      }
    }

    length = store.size() - storeClause;
    store.setQuick(storeClause - 1, encode(length, OR));
    cleanLastClause(store, storeClause);
  }

  /**
   * Cleans last clauses added to store, removing it
   * if the clause is a tautology.
   */
  private void cleanLastClause(final TIntArrayList store,
                               final int clause) {
    touched.reset();

    int length = length(store, clause);
    int p = clause;
    for (int i = clause; i < clause + length; i++) {
      int u = store.getQuick(i);
      if (touched.contains(neg(u))) {
        // logger.info("tautology");
        store.remove(clause - 1, store.size() - clause + 1);
        return;
      } else if (!touched.containsOrAdd(u)) {
        store.setQuick(p, u);
        p++;
      }
    }

    store.remove(p, store.size() - p);
    store.setQuick(clause - 1, encode(p - clause, OR));
  }
}
