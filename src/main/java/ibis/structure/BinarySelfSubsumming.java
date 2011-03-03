package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.iterator.TIntIterator;;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/** Performs binary (self)subsumming. */
public class BinarySelfSubsumming {
  private static final Logger logger = Logger.getLogger(BinarySelfSubsumming.class);

  public static void run(final Solver solver)
      throws ContradictionException {
    // long start = System.currentTimeMillis();
    
    int numVariables = solver.numVariables;
    TouchSet touched = new TouchSet(numVariables);
    TIntArrayList tautologies = new TIntArrayList();
    for (int u = -numVariables; u <= numVariables; u++) {
      if (u != 0 && solver.numBinaries(u) > 0 && !solver.watchLists.get(u).isEmpty()) {
        tautologies.reset();
        touched.reset();
        solver.graph.dfs(-u, touched);

        run(solver, u, touched, tautologies);

        // Removes discovered tautologies.
        for (int i = 0; i < tautologies.size(); i++) {
          solver.watchLists.removeClause(tautologies.get(i));
        }
      }
    }

    // long end = System.currentTimeMillis();
    // logger.info("bsss took " + (end - start) / 1000.);
    // System.exit(1);
  }

  private static void run(final Solver solver,
                          final int u,
                          final TouchSet touched,
                          final TIntArrayList tautologies) {

    TIntHashSet watchList = solver.watchLists.get(u);
    TIntArrayList formula = solver.watchLists.formula();
    TIntIterator it = watchList.iterator();

clause_loop:
    for (int size = watchList.size(); size > 0; size--) {
      int clause = it.next();
      if (type(formula, clause) != OR) {
        continue;
      }
      int length = length(formula, clause);

      // Checks if clause is a tautology
      for (int j = clause; j < clause + length; j++) {
        int v = formula.get(j);
        if (v != u && touched.contains(v)) {
          // If u + v + a + ... and -u => v
          // then clause is tautology
          tautologies.add(clause);
          continue clause_loop;
        }
      }

      // Removes extra literals
      for (int j = clause; j < clause + length; j++) {
        int v = formula.get(j);
        if (v != u && touched.contains(-v)) {
          // If a + b + c + ... and -a => -b
          // then a + c + ...
          solver.watchLists.removeLiteralAt(clause, j);
          length--;
          j--;
        }
      }
    }

  }
}
 
