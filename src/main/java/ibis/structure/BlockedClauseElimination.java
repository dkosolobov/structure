package ibis.structure;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TIntStack;
import org.apache.log4j.Logger;


import static ibis.structure.Misc.*;


public final class BlockedClauseElimination {
  private static final Logger logger = Logger.getLogger(Solver.class);

  private final Solver solver;
  private final int numVariables;
  private final TIntArrayList formula;
  private final TouchSet seen;

  public BlockedClauseElimination(final Solver solver) {
    this.solver = solver;
    
    numVariables = solver.numVariables;
    formula = solver.watchLists.formula();
    seen = new TouchSet(numVariables);
  }

  public static void addUnits(final TIntArrayList bceClauses, final BitSet units) {
    ClauseIterator it = new ClauseIterator(bceClauses);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(bceClauses, clause);
      int literal = bceClauses.get(clause);

      boolean satisfied = false;
      for (int i = clause; i < clause + length; i++) {
        if (units.contains(bceClauses.getQuick(i))) {
          satisfied = true;
          break;
        }
      }

      if (!satisfied) {
        units.remove(-literal);
        units.add(literal);
      }
    }
  }


  public void run() {
    long start_ = System.currentTimeMillis();
    // logger.info("running on " + formulaToString(formula));

    solver.bceClauses = new TIntArrayList(); 
    TIntArrayList blocked = new TIntArrayList();;
    int numBlocked = 0;
    int numLiterals = 0;

    for (int literal = -numVariables; literal <= numVariables; literal++) {
      if (solver.numBinaries(-literal) != 0) {
        continue;
      }

      int ne = solver.watchLists.get(-literal).size();
      int pe = solver.watchLists.get(literal).size();
      if (ne == 0) {
        // Ignores pure literals.
        continue;
      }
      if (ne > 100 && 1L * ne * pe > 10000) {
        // This is a cutoff to avoid very expensive literals.
        continue;
      }

      if (hasXORClauses(literal)) {
        // BCE can't handle xor clauses.
        continue;
      }

      blocked.reset();
      TIntHashSet clauses = solver.watchLists.get(literal);
      TIntIterator it = clauses.iterator();
      for (int size = clauses.size(); size > 0; size--) {
        int clause = it.next();
        if (type(formula, clause) == OR && isBlocked(literal, clause)) {
          blocked.add(clause);
          // logger.info("blocked clause " + clauseToString(formula, clause) + " by " + literal);
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

        // Puts the clause in reverse order.
        for (int j = clause + 1; j < clause + length; j++) {
          solver.bceClauses.add(formula.getQuick(j));
        }
        solver.bceClauses.add(literal);
        solver.bceClauses.add(encode(length, OR));

        solver.watchLists.removeClause(clause);
      }
    }

    solver.bceClauses.reverse();

    long end_ = System.currentTimeMillis();
    if (Configure.verbose) {
      if (numBlocked > 0) {
        System.err.print("bc" + numBlocked + ".");
      }
    }
    // logger.info("Found " + numBlocked + " (" + numLiterals + ") in " + (end_ - start_) / 1000.);
  }

  /** Returns true if there exists a XOR clause containing literal. */
  private boolean hasXORClauses(int literal) {
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
  private boolean isBlocked(int literal, int clause) {
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
  private boolean isResolutionTautology(int clause) {
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
