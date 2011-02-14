package ibis.structure;

/** Assigns literals that appear only as plus or as minus. */
public class PureLiterals {
  /** Performs hyper-binary resolution */
  public static boolean run(final Solver solver) throws ContradictionException {
    int numUnits = 0;
    for (int u = 1; u <= solver.numVariables; u++) {
      if (!solver.isLiteralAssigned(u)) {
        if (solver.numBinaries(u) == 0 && solver.watchList(u).size() == 0) {
          solver.queueUnit(-u);
          numUnits++;
          continue;
        }
        if (solver.numBinaries(-u) == 0 && solver.watchList(-u).size() == 0) {
          solver.queueUnit(u);
          numUnits++;
          continue;
        }
      }
    }

    if (Configure.verbose) {
      if (numUnits > 0) {
        System.err.print("p" + numUnits + ".");
      }
    }
    return numUnits > 0;
  }
}
