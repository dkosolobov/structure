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

  private final Solver solver;
  private final int numVariables;
  private final TIntArrayList formula;
  private final TouchSet seen;

  public BlockedClauseElimination(final Solver solver) {
    this.solver = solver;
    
    numVariables = solver.numVariables;
    formula = solver.formula;
    seen = new TouchSet(numVariables);
  }

  public static TIntArrayList run(final Solver solver) {
    return (new BlockedClauseElimination(solver)).run();
  }

  /** Fixes units to satisfy blocked clauses. */
  public static Solution restore(final TIntArrayList bce,
                                 final Solution solution) {
    if (!solution.isSatisfiable() || bce == null || bce.isEmpty()) {
      return solution;
    }

    TIntHashSet units = new TIntHashSet(solution.units());
    ClauseIterator it = new ClauseIterator(bce);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(bce, clause);
      int literal = bce.get(clause);

      if (!units.contains(neg(literal))) {
        units.add(literal);
        continue;
      }

      boolean satisfied = false;
      for (int i = clause; i < clause + length; i++) {
        if (units.contains(bce.getQuick(i))) {
          satisfied = true;
          break;
        }
      }

      if (!satisfied) {
        units.remove(neg(literal));
        units.add(literal);
      }
    }

    return Solution.satisfiable(units.toArray());
  }

  /**
   * For all literals finds clauses blocked on that literal. <br/>
   *
   * Returns a list of blocked clauses.
   */
  private TIntArrayList run() {
    TIntArrayList bce = new TIntArrayList(); 
    TIntArrayList blocked = new TIntArrayList();;
    int numBlocked = 0;
    int numLiterals = 0;

    for (int literal = -numVariables; literal <= numVariables; literal++) {
      int ne = solver.watchLists.get(neg(literal)).size();
      int pe = solver.watchLists.get(literal).size();
      if (ne > 100 || 1L * ne * pe > 10000L) {
        // This is a cutoff to avoid very expensive literals.
        continue;
      }
      if (hasXORClauses(literal)) {
        // BCE can't handle xor clauses.
        continue;
      }

      // Checks each clause containing literal if it is blocked on literal.
      blocked.reset();
      TIntHashSet clauses = solver.watchLists.get(literal);
      TIntIterator it = clauses.iterator();
      for (int size = clauses.size(); size > 0; size--) {
        int clause = it.next();
        if (type(formula, clause) == OR && isBlocked(literal, clause)) {
          blocked.add(clause);
        }
      }

      numBlocked += blocked.size();
      for (int i = 0; i < blocked.size(); i++) {
        int clause = blocked.getQuick(i);
        int length = length(formula, clause);
        numLiterals += length;

        // Moves blocked literal in front of the clause
        int p = formula.indexOf(clause, literal);
        formula.setQuick(p, formula.getQuick(clause));
        formula.setQuick(clause, literal);

        // Puts the clauses in reverse order.
        for (int j = clause + 1; j < clause + length; j++) {
          bce.add(formula.getQuick(j));
        }
        bce.add(literal);
        bce.add(encode(length, OR));

        solver.watchLists.removeClause(clause);
      }
    }

    // Solution is reconstructed starting from last removed
    // blocked clause.
    bce.reverse();

    logger.info("Found " + bce.size() + " literals in "
                + numBlocked + " blocked clauses");
    return bce;
  }

  /** Returns true if there exists a XOR clause containing literal. */
  private boolean hasXORClauses(final int literal) {
    TIntHashSet clauses = solver.watchLists.get(var(literal));
    TIntIterator it = clauses.iterator();
    for (int size = clauses.size(); size > 0; size--) {
      int clause = it.next();
      if (type(formula, clause) != OR) {
        return true;
      }
    }
    return false;
  }

  /** Returns true if literal blocks clause. */
  private boolean isBlocked(final int literal, final int clause) {
    seen.reset();
    int length = length(formula, clause);
    for (int i = clause; i < clause + length; i++) {
      seen.add(formula.getQuick(i));
    }

    TIntHashSet clauses = solver.watchLists.get(-literal);
    TIntIterator it = clauses.iterator();
    for (int size = clauses.size(); size > 0; size--) {
      if (!isResolutionTautology(it.next())) {
        return false;
      }
    }

    return true;
  }

  /**
   * Checks if resolution between clause and clause stored in seen is a tautology.
   *
   * clause and clause marked in seen already have one variable in common (i.e.
   * the one currently tested in run()) of oposite signs.
   */
  private boolean isResolutionTautology(final int clause) {
    int length = length(formula, clause);
    int count = 0;

    for (int i = clause; i < clause + length; i++) {
      if (seen.contains(-formula.getQuick(i))) {
        count++;
        if (count == 2) {
          return true;
        }
      }
    }

    return false;
  }
}
