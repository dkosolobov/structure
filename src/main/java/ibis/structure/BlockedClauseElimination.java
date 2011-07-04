package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.iterator.TIntIterator;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/**
 * Performes <a href="http://fmv.jku.at/papers/JarvisaloBiereHeule-TACAS10.pdf">
 * Blocked Clause Elimination</a>.
 */
public final class BlockedClauseElimination {
  private static final Logger logger = Logger.getLogger(Solver.class);

  /** Solver containing the instance. */
  private final Solver solver;
  /** Literals seen in the tested clause. */
  private final TouchSet seen;
  /** Literals hidden with current literal. */
  private final TouchSet hidden;

  public BlockedClauseElimination(final Solver solver) {
    this.solver = solver;

    seen = new TouchSet(solver.numVariables);
    hidden = new TouchSet(solver.numVariables);
  }

  public static TIntArrayList run(final Solver solver) {
    return (new BlockedClauseElimination(solver)).run();
  }

  /** Fixes units to satisfy blocked clauses. */
  public static Solution restore(final TIntArrayList bce,
                                 final Solution solution) {
    assert solution.isSatisfiable() && bce != null;
    TIntHashSet units = new TIntHashSet(solution.units());
    ClauseIterator it = new ClauseIterator(bce);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(bce, clause);
      int literal = bce.get(clause);

      boolean satisfied = false;
      for (int i = clause; i < clause + length; i++) {
        int u = bce.getQuick(i);
        if (!units.contains(neg(u))) {  // Literal may be missing.
          units.add(u);
        }
        if (units.contains(u)) {
          satisfied = true;
        }
      }

      if (!satisfied) {
        units.remove(neg(literal));
        units.add(literal);
      }
    }

    return Solution.satisfiable(units);
  }

  /**
   * For all literals finds clauses blocked on that literal. <br/>
   *
   * @return a list of blocked clauses.
   */
  private TIntArrayList run() {
    solver.propagateBinaries();

    TIntArrayList bce = new TIntArrayList();
    TIntArrayList blocked = new TIntArrayList();;
    int numBlocked = 0;
    int numLiterals = 0;

    for (int iz = 0; iz < 3; iz++) {
      for (int literal = -solver.numVariables;
          literal <= solver.numVariables;
          literal++) {
        int ne = solver.watchLists.get(neg(literal)).size();
        int pe = solver.watchLists.get(literal).size();
        if (ne > 128 || 1L * ne * pe > 16384L) {
          // This is a cutoff to avoid very expensive literals.
          continue;
        }
        if (Configure.xor && hasXORClauses(literal)) {
          // BCE can't handle xor clauses.
          continue;
        }

        // Finds hidden literals by current literal
        hidden.reset();
        TIntArrayList foobar = new TIntArrayList();
        solver.graph.bfs(neg(literal), foobar);
        for (int i = 0; i < foobar.size(); i++) {
          hidden.add(neg(literal));
        }

        // Checks each clause containing literal if it is blocked on literal.
        blocked.reset();
        TIntHashSet clauses = solver.watchLists.get(literal);
        TIntIterator it = clauses.iterator();
        for (int size = clauses.size(); size > 0; size--) {
          int clause = it.next();
          if (type(solver.formula, clause) == OR && isBlocked(literal, clause)) {
            blocked.add(clause);
          }
        }

        numBlocked += blocked.size();
        for (int i = 0; i < blocked.size(); i++) {
          int clause = blocked.getQuick(i);
          int length = length(solver.formula, clause);
          numLiterals += length;

          // Moves blocked literal in front of the clause
          int p = solver.formula.indexOf(clause, literal);
          solver.formula.setQuick(p, solver.formula.getQuick(clause));
          solver.formula.setQuick(clause, literal);

          // Puts the clauses in reverse order.
          for (int j = clause + 1; j < clause + length; j++) {
            bce.add(solver.formula.getQuick(j));
          }
          bce.add(literal);
          bce.add(encode(length, OR));

          solver.watchLists.removeClause(clause);
        }
      }
    }

    // Solution is reconstructed starting from last removed
    // blocked clause.
    bce.reverse();

    logger.info("Found " + bce.size() + " literals in "
                + numBlocked + " blocked clauses");
    return bce;
  }

  /**
   * Returns true if there exists a XOR clause containing literal.
   *
   * @param literal literal to search in XOR clauses
   * @return true if there is XOR clause containing literal
   */
  private boolean hasXORClauses(final int literal) {
    TIntHashSet clauses = solver.watchLists.get(var(literal));
    TIntIterator it = clauses.iterator();
    for (int size = clauses.size(); size > 0; size--) {
      int clause = it.next();
      if (type(solver.formula, clause) != OR) {
        return true;
      }
    }
    return false;
  }

  /**
   * Tests if literal blocks clause.
   *
   * @param literal blocked literal
   * @param clause blocked clause
   * @return true if clause is blocked on literal
   */
  private boolean isBlocked(final int literal, final int clause) {
    seen.reset();
    int length = length(solver.formula, clause);
    for (int i = clause; i < clause + length; i++) {
      seen.add(solver.formula.getQuick(i));
    }

    TIntHashSet clauses = solver.watchLists.get(neg(literal));
    TIntIterator it = clauses.iterator();
    for (int size = clauses.size(); size > 0; size--) {
      if (!isResolutionTautology(it.next())) {
        return false;
      }
    }

    return true;
  }

  /**
   * Checks if resolution between clause and clause
   * marked in seen is a tautology.
   *
   * clause and clause marked in seen already have one
   * variable in common (i.e.  * the one currently tested
   * in run()) of oposite signs. If thereis a second variable,
   * then resolution is a tautology.
   *
   * @param clause clause to be resoluted and checked
   * @param true if resolution is a tautology
   */
  private boolean isResolutionTautology(final int clause) {
    int length = length(solver.formula, clause);
    boolean found = false;

    for (int i = clause; i < clause + length; i++) {
      int literal = neg(solver.formula.getQuick(i));
      if (seen.contains(literal) || hidden.contains(literal)) {
        if (found) {
          return true;
        }
        found = true;
      }
    }

    return false;
  }
}
