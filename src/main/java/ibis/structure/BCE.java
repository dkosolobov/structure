package ibis.structure;

import gnu.trove.TIntArrayList;
import org.apache.log4j.Logger;

public class BCE {
  private static final Logger logger = Logger.getLogger(Solver.class);

  private TIntArrayList blockedClauses;

  public BCE(TIntArrayList blockedClauses) {
    this.blockedClauses = blockedClauses;
  }

  public void patch(int[] units) {
    if (blockedClauses.isEmpty()) {
      return;
    }

    // logger.info("fixing " + blockedClauses);
    // logger.info("units are " + (new TIntArrayList(units)));
    BitSet all = new BitSet();
    all.addAll(units);


    blockedClauses.remove(blockedClauses.size() - 1);
    blockedClauses.reverse();
    blockedClauses.add(0);

    boolean satisfied = false;
    for (int i = 0; i < blockedClauses.size(); i++) {
      int literal = blockedClauses.get(i);
      if (literal == 0) {
        if (!satisfied) {
          int blockedLiteral = blockedClauses.get(i - 1);
          // logger.info("patching " + blockedLiteral);
          assert all.contains(-blockedLiteral) : "Blocked literal " + -blockedLiteral + " not a unit";
          all.remove(-blockedLiteral);
          all.add(blockedLiteral);
        }

        satisfied = false;
        continue;
      }

      if (all.contains(literal)) {
        satisfied = true;
      }
    }

    assert all.elements().length == units.length;
    System.arraycopy(all.elements(), 0, units, 0, units.length);
    // logger.info("newunits are " + (new TIntArrayList(units)));
  }
}
