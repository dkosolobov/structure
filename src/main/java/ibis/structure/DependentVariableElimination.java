package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/**
 * Performs Dependent Variable Elimination.
 *
 * A variable is dependent if it appears in exactly one XOR clause.
 */
public final class DependentVariableElimination {
  private static final Logger logger = Logger.getLogger(DependentVariableElimination.class);

  public static TIntArrayList run(final Solver solver) {
    int numVariables = solver.numVariables;
    TIntArrayList formula = solver.watchLists.formula();
    TIntArrayList dve = new TIntArrayList();

    int numDependent = 0;
    for (int u = 1; u <= numVariables; u++) {
      if (!isDependent(solver, u)) {
        continue;
      }

      int clause = solver.watchLists.get(u).toArray()[0];
      int type = type(formula, clause);
      if (type == OR) {
        continue;
      }

      // u appears in a single XOR/NXOR clause
      // Moves literal in front of the clause.
      int p = formula.indexOf(clause, u);
      formula.setQuick(p, formula.getQuick(clause));
      formula.setQuick(clause, u);

      int length = length(formula, clause);
      dve.add(encode(length, type));
      for (int i = clause; i < clause + length; i++) {
        int literal = formula.getQuick(i);
        dve.add(literal);
      }

      numDependent++;
      solver.watchLists.removeClause(clause);
    }

    if (Configure.verbose) {
      if (numDependent > 0) {
        System.err.print("dv" + numDependent + ".");
      }
    }

    return dve;
  }

  /** Fixes units to satisfy clauses with dependent variables */
  public static void addUnits(final TIntArrayList dve, final BitSet units) {
    ClauseIterator it = new ClauseIterator(dve);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(dve, clause);
      int type = type(dve, clause);

      // Literal was assigned because after DVE it is missing from formula.
      int literal = dve.get(clause);
      units.remove(literal);
      units.remove(neg(literal));

      int xor = 0;
      for (int i = clause + 1; i < clause + length; i++) {
        int u = dve.getQuick(i);
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

  private static boolean isDependent(final Solver solver, final int u) {
    return solver.watchLists.get(neg(u)).size() == 0
        && solver.numBinaries(neg(u)) == 0
        && solver.watchLists.get(u).size() == 1
        && solver.numBinaries(u) == 0;
  }
}
