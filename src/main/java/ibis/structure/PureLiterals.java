package ibis.structure;

import gnu.trove.iterator.TIntIterator;

import static ibis.structure.Misc.*;


/** Assigns variables that appear only as phase in OR clauses. */
public class PureLiterals {
  public static boolean run(final Solver solver) throws ContradictionException {
    final WatchLists wl = solver.watchLists;
    int numUnits = 0;

  loop_variables:
    for (int u = 1; u <= solver.numVariables; u++) {
      if (solver.isLiteralAssigned(u)) {
        continue;
      }

      // If u is in a XOR clause it is not pure.
      TIntIterator it = wl.get(u).iterator();
      for  (int size = wl.get(u).size(); size > 0; size--) {
        int clause = it.next();
        int type = type(solver.formula, clause);
        if (type != OR) {
          continue loop_variables;
        }
      }

      // Checks if -u is a pure literal
      if (wl.get(u).isEmpty()) {
        solver.queueUnit(-u);
        numUnits++;
        continue;
      }

      // Checks if u is a pure literal
      if (wl.get(neg(u)).isEmpty()) {
        solver.queueUnit(u);
        numUnits++;
        continue;
      }
    }

    if (Configure.verbose) {
      if (numUnits > 0) {
        System.err.print("p" + numUnits + ".");
      }
    }

    solver.propagate();
    return numUnits > 0;
  }
}
