package ibis.structure;

import gnu.trove.TIntArrayList;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class DependentVariableElimination {
  private static final Logger logger = Logger.getLogger(DependentVariableElimination.class);

  public static void run(final Solver solver) {
    int numVariables = solver.numVariables;
    TIntArrayList formula = solver.watchLists.formula();
    TIntArrayList dvClauses = new TIntArrayList();
    solver.dvClauses = dvClauses;

    int numDependent = 0;

    for (int u = 1; u <= numVariables; u++) {
      boolean dependent = true;
      dependent = dependent && solver.watchLists.get(neg(u)).size() == 0;
      dependent = dependent && solver.numBinaries(neg(u)) == 0;
      dependent = dependent && solver.watchLists.get(u).size() == 1;
      dependent = dependent && solver.numBinaries(u) == 0;
      if (!dependent) {
        continue;
      }

      int clause = solver.watchLists.get(u).toArray()[0];
      int length = length(formula, clause);
      int type = type(formula, clause);
      if (type == OR) {
        continue;
      }

      // u appears in a single XOR/NXOR clause
      // Moves literal in front of the clause.
      int p = formula.indexOf(clause, u);
      formula.setQuick(p, formula.getQuick(clause));
      formula.setQuick(clause, u);

      dvClauses.add(encode(length, type));
      for (int i = clause; i < clause + length; i++) {
        dvClauses.add(formula.getQuick(i));
      }

      numDependent++;
      solver.watchLists.removeClause(clause);
    }

    if (Configure.verbose) {
      if (numDependent > 0) {
        System.err.print("dv" + numDependent + ".");
      }
    }
  }

  public static void addUnits(final TIntArrayList dvClauses, final BitSet units) {
    ClauseIterator it = new ClauseIterator(dvClauses);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(dvClauses, clause);
      int type = type(dvClauses, clause);

      // Literal was assigned because after DVE it is missing from formula.
      int literal = dvClauses.get(clause);
      units.remove(literal);
      units.remove(neg(literal));

      int xor = 0;
      for (int i = clause + 1; i < clause + length; i++) {
        int u = dvClauses.getQuick(i);
        if (units.contains(u)) {
          xor ^= 1;
        }
      }

      if (type == XOR) {
        if (xor == 1) {
          units.add(neg(literal));
        } else {
          units.add(literal);
        }
      } else {
        assert type == NXOR;
        if (xor == 1) {
          units.add(literal);
        } else {
          units.add(neg(literal));
        }
      }
    }
  }
}
