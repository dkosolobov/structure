package ibis.structure;

import gnu.trove.TIntArrayList;

/** Performs binary (self)subsumming. */
public class BinarySelfSubsumming {
  /** @return true if any literal was removed. */
  public static boolean run(final Solver solver) throws ContradictionException {
    int numSatisfiedClauses = 0, numRemovedLiterals = 0;
    TouchSet touched = new TouchSet(solver.numVariables);

    for (int start : solver.lengths().keys()) {
      // Finds the end of the clause
      int end = start - 1;
      while (solver.clauses.get(++end) != 0) {
      }

    search:
      for (int i = start; i < end; ++i) {
        int first = solver.clauses.get(i);
        if (first == solver.REMOVED) {
          continue;
        }

        TIntArrayList edges = solver.graph.edges(-first);
        touched.reset();
        for (int j = 0; j < edges.size(); j++) {
          touched.add(edges.get(j));
        }

        for (int j = start; j < end; ++j) {
          int second = solver.clauses.get(j);
          if (i == j || second == solver.REMOVED) {
            continue;
          }

          if (touched.contains(-second)) {
            // If a + b + c + ... and -a => -b
            // then a + c + ...
            solver.removeLiteral(start, second, j);
            ++numRemovedLiterals;
            continue;
          }

          if (touched.contains(second)) {
            // If a + b + c + ... and -a => b
            // then clause is tautology
            solver.removeClause(start);
            ++numRemovedLiterals;
            break search;
          }
        }
      }

      start = end + 1;
    }

    if (Configure.verbose) {
      if (numRemovedLiterals > 0) {
        System.err.print("bl" + numRemovedLiterals + ".");
      }
      if (numSatisfiedClauses > 0) {
        System.err.print("bc" + numSatisfiedClauses + ".");
      }
    }
    return numRemovedLiterals > 0 || numSatisfiedClauses > 0;
  }
}
 
