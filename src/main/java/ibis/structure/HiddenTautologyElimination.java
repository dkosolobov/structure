package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.iterator.TIntIterator;;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/**
 * Performs Hidden Tautology Elimination until a fixed point.<br/>
 *
 * If a + b + c + ... is a clause
 * and -a &rarr; c then the clause is always satisfied and can be removed.<br/>
 *
 * HTE as described in this <a href="http://www.springerlink.com/content/4376742562145301/">paper</a>
 * requires Hidden Literal Addition, which is performed by traversing the implication graph. <br/>
 *
 */
public class HiddenTautologyElimination {
  private static final Logger logger = Logger.getLogger(HiddenTautologyElimination.class);

  private Solver solver;
  private TouchSet touched;
  private TIntArrayList tautologies;
  private int numRemovedLiterals = 0;

  private HiddenTautologyElimination(final Solver solver) {
    this.solver = solver;

    touched = new TouchSet(solver.numVariables);
    tautologies = new TIntArrayList();
  }

  public static void run(final Solver solver) throws ContradictionException {
    (new HiddenTautologyElimination(solver)).run();
  }

  private void run() throws ContradictionException {
    for (int literal = -solver.numVariables;
        literal <= solver.numVariables;
        literal++) {
      if (literal == 0
          || solver.numBinaries(literal) == 0
          || solver.watchLists.get(literal).isEmpty()) {
        continue;
      }

      tautologies.reset();
      touched.reset();
      solver.graph.dfs(neg(literal), touched);
      eliminate(literal);

      // Removes discovered tautologies.
      for (int i = 0; i < tautologies.size(); i++) {
        solver.watchLists.removeClause(tautologies.get(i));
      }
    }

    if (Configure.verbose) {
      if (numRemovedLiterals > 0) {
        System.err.print("hte" + numRemovedLiterals + ".");
      }
    }
  }

  private void eliminate(final int literal) throws ContradictionException {
    TIntHashSet watchList = solver.watchLists.get(literal);
    TIntIterator it = watchList.iterator();

clause_loop:
    for (int size = watchList.size(); size > 0; size--) {
      int clause = it.next();
      int length = length(solver.formula, clause);

      if (length < 3) {
        continue;
      }
      if (type(solver.formula, clause) != OR) {
        continue;
      }

      // Checks if clause is a tautology
      for (int j = clause; j < clause + length; j++) {
        int v = solver.formula.getQuick(j);
        if (v != literal && touched.contains(v)) {
          // If literal + v + a + ... and -literal => v
          // then clause is tautology
          numRemovedLiterals += length;
          tautologies.add(clause);
          continue clause_loop;
        }

        if (v != literal && touched.contains(neg(v))) {
          numRemovedLiterals++;
          length--;
          solver.watchLists.removeLiteral(clause, v);
        }
      }
    }
  }
}
 
