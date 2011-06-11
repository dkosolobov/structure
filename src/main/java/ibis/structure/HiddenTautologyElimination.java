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

  private final Solver solver;
  private final int numVariables;
  private final TIntArrayList formula;
  private final TouchSet touched;
  private final TIntArrayList tautologies;

  private HiddenTautologyElimination(final Solver solver) {
    this.solver = solver;

    numVariables = solver.numVariables;
    formula = solver.formula;
    touched = new TouchSet(numVariables);
    tautologies = new TIntArrayList();
  }

  public static boolean run(final Solver solver) {
    return (new HiddenTautologyElimination(solver)).run();
  }

  private boolean run() {
    int numTautologies = 0;

    for (int literal = -numVariables; literal <= numVariables; literal++) {
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
      numTautologies += tautologies.size();
      for (int i = 0; i < tautologies.size(); i++) {
        solver.watchLists.removeClause(tautologies.get(i));
      }
    }

    if (Configure.verbose) {
      if (numTautologies > 0) {
        System.err.print("hte" + numTautologies + ".");
      }
    }
    return numTautologies > 0;
  }

  private void eliminate(final int literal) {
    TIntHashSet watchList = solver.watchLists.get(literal);
    TIntIterator it = watchList.iterator();

clause_loop:
    for (int size = watchList.size(); size > 0; size--) {
      int clause = it.next();
      int length = length(formula, clause);

      if (length < 3) {
        continue;
      }
      if (type(formula, clause) != OR) {
        continue;
      }

      // Checks if clause is a tautology
      for (int j = clause; j < clause + length; j++) {
        int v = formula.getQuick(j);
        if (v != literal && touched.contains(v)) {
          // If literal + v + a + ... and -literal => v
          // then clause is tautology
          tautologies.add(clause);
          continue clause_loop;
        }
      }
    }
  }
}
 
