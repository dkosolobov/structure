package ibis.structure;

/** Assigns literals don't appear at all in the instance.  */
public class MissingLiterals {
  public static boolean run(final Solver solver) throws ContradictionException {
    int numUnits = 0;
    for (int u = 1; u <= solver.numVariables; u++) {
      if (solver.isVariableMissing(u)) {
        solver.queueUnit(u);
        numUnits++;
      }
    }

    if (Configure.verbose) {
      if (numUnits > 0) {
        System.err.print("m" + numUnits + ".");
      }
    }
    return numUnits > 0;
  }
}
