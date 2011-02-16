package ibis.structure;

import gnu.trove.TIntArrayList;

import static ibis.structure.Misc.*;


/** Performs binary (self)subsumming. */
public class BinarySelfSubsumming {
  /** @return true if any literal was removed. */
  public static boolean run(final Solver solver)
      throws ContradictionException {
    final TIntArrayList formula = solver.watchLists.formula;

    int numRemovedClauses = 0;
    int numRemovedLiterals = 0;
    TouchSet touched = new TouchSet(solver.numVariables);

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      if (type(formula, clause) != OR) {
        continue;
      }

      int length = length(formula, clause);

    search:
      for (int i = clause; i < clause + length; i++) {
        int u = formula.get(i);

        touched.reset();
        touched.add(-u);
        TIntArrayList edges = solver.graph.edges(-u);
        for (int j = 0; j < edges.size(); j++) {
          touched.add(edges.get(j));
        }

        // Checks if clause is a tautology
        for (int j = clause; j < clause + length; j++) {
          int v = formula.get(j);
          if (j != i && touched.contains(v)) {
            // If u + v + a + ... and -u => v
            // then clause is tautology
            solver.watchLists.removeClause(clause);
            numRemovedClauses++;
            break search;
          }
        }

        // Removes extra literals
        for (int j = clause; j < clause + length; j++) {
          int v = formula.get(j);
          if (j != i && touched.contains(-v)) {
            // If a + b + c + ... and -a => -b
            // then a + c + ...
            solver.watchLists.removeLiteralAt(clause, j);
            length--;
            i -= i > j ? 1 : 0;
            j--;
            numRemovedLiterals++;
          }
        }
      }
    }

    if (Configure.verbose) {
      if (numRemovedLiterals > 0) {
        System.err.print("bl" + numRemovedLiterals + ".");
      }
      if (numRemovedClauses > 0) {
        System.err.print("bc" + numRemovedClauses + ".");
      }
    }
    return numRemovedLiterals > 0 || numRemovedClauses > 0;
  }
}
 
