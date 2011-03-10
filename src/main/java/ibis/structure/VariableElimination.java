package ibis.structure;

import java.util.Vector;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.hash.TIntIntHashMap;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

public class VariableElimination {
  private static final Logger logger = Logger.getLogger(VariableElimination.class);

  private static class Data {
    public int literal;
    public TIntArrayList clauses;
  }

  private final Solver solver;
  private final int numVariables;
  private final WatchLists watchLists;
  private final TIntArrayList formula;
  private final ImplicationsGraph graph;
  private final TouchSet touched;

  private VariableElimination(final Solver solver) {
    this.solver = solver;

    numVariables = solver.numVariables;
    watchLists = solver.watchLists;
    formula = solver.watchLists.formula();
    graph = solver.graph;
    touched = new TouchSet(numVariables);
  }

  public static void run(final Solver solver) throws ContradictionException {
    (new VariableElimination(solver)).run();
  }

  public static Solution restore(final Object ve_, final Solution solution) {
    // logger.info("Restoring on " + ve_);

    if (!solution.isSatisfiable() || ve_ == null) {
      return solution;
    }

    Vector<Data> ve = (Vector<Data>) ve_;
    TIntHashSet units = new TIntHashSet(solution.units());


    for (int i = ve.size() - 1; i >= 0; i--) {
      Data data = ve.get(i);

      units.remove(neg(data.literal));
      units.add(data.literal);

      // logger.info("Restoring " + data.literal);
      // logger.info("units " + units);
      // logger.info("clauses " + formulaToString(data.clauses));

      ClauseIterator it = new ClauseIterator(data.clauses);
      while (it.hasNext()) {
        int clause = it.next();
        if (!isClauseSatisfied(data.clauses, clause, units)) {
          // logger.info("satisfiying clause " + clauseToString(data.clauses, clause));
          units.remove(data.literal);
          units.add(neg(data.literal));
          assert isClauseSatisfied(data.clauses, clause, units);
        }
      }
    }

    // logger.info("new units " + units);
    // logger.info("------------");
    return Solution.satisfiable(units.toArray());
  }

  private void run() throws ContradictionException {
    // logger.info("VE on " + formulaToString(formula));

    Vector<Data> ve = new Vector<Data>();
    for (int literal = 1; literal <= numVariables; literal++) {
      Data data = eliminate(literal);
      if (data != null) {
        ve.add(data);
      }
    }

    // logger.info("Eliminated " + ve.size() + " variables");
    solver.ve = ve;

    if (Configure.verbose) {
      if (!ve.isEmpty()) {
        System.err.print("ve" + ve.size() + ".");
      }
    }
  }

  private Data eliminate(final int literal)
      throws ContradictionException {
    // Checks that variable is suitable for elimination.
    if (solver.isLiteralAssigned(literal)
        || solver.numClauses(literal) * solver.numClauses(neg(literal)) > 25
        || solver.numClauses(literal) == 0
        || solver.numClauses(neg(literal)) == 0) {
      return null;
    }

    // Clauses must be short enough and not XORs.
    int[] p = watchLists.get(literal).toArray();
    int[] n = watchLists.get(neg(literal)).toArray();
    for (int i = 0; i < p.length; i++) {
      if (length(formula, p[i]) > 16 || type(formula, p[i]) != OR) {
        return null;
      }
    }
    for (int i = 0; i < n.length; i++) {
      if (length(formula, n[i]) > 16 || type(formula, n[i]) != OR) {
        return null;
      }
    }

    TIntArrayList store = resolution(literal, p, n);
    watchLists.append(store);

    Data data = new Data();
    data.literal = literal;
    data.clauses = removeLiteral(literal, p, n);

    return data;
  }

  private TIntArrayList removeLiteral(final int literal, int[] p, int[] n) {
    TIntArrayList clauses = new TIntArrayList();

    for (int i = 0; i < p.length; i++) {
      copy(clauses, formula, p[i]);
      watchLists.removeClause(p[i]);
    }
    for (int i = 0; i < n.length; i++) {
      copy(clauses, formula, n[i]);
      watchLists.removeClause(n[i]);
    }

    TIntArrayList edges;
    edges = graph.edges(literal);
    for (int j = 0; j < edges.size(); j++) {
      clauses.add(encode(2, OR));
      clauses.add(neg(literal));
      clauses.add(edges.getQuick(j));
    }

    edges = graph.edges(neg(literal));
    for (int j = 0; j < edges.size(); j++) {
      clauses.add(encode(2, OR));
      clauses.add(literal);
      clauses.add(edges.getQuick(j));
    }

    graph.remove(literal);

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
    TIntArrayList pEdges = graph.edges(literal);
    for (int i = 0; i < p.length; i++) {
      for (int j = 0; j < pEdges.size(); j++) {
        binaryResolution(store, p[i], neg(literal), pEdges.getQuick(j));
      }
    }

    // binary - clause
    TIntArrayList nEdges = graph.edges(neg(literal));
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
    int length = length(formula, first);
    for (int i = first; i < first + length; i++) {
      int u = formula.getQuick(i);
      if (u != literal) {
        store.add(u);
      }
    }

    // Adds literals from the second storeClause.
    length = length(formula, second);
    for (int i = second; i < second + length; i++) {
      int u = formula.getQuick(i);
      if (u != neg(literal)) {
        store.add(u);
      }
    }

    // Sets the length and type.
    length = store.size() - storeClause;
    store.setQuick(storeClause - 1, encode(length, OR));
    cleanLastClause(store, storeClause);

    /*
    if (numVariables < 16) {
      logger.info("Resolution on " + literal);
      logger.info("first is " + clauseToString(formula, first));
      logger.info("second is " + clauseToString(formula, first));
      if (!isClauseRemoved(store, storeClause)) {
        logger.info("result is " + clauseToString(store, storeClause));
      }
    }
    */
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
    /*
    if (numVariables < 16) {
      logger.info("binary resolution " + clauseToString(formula, clause));
      logger.info("with " + first + " or " + second);
    }
    */

    if (first == neg(second)) {
      // Ignores binary tautologies.
      return;
    }

    store.add(0);
    int storeClause = store.size();

    // Adds literals from the first clause.
    int length = length(formula, clause);
    for (int i = clause; i < clause + length; i++) {
      int u = formula.getQuick(i);
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
