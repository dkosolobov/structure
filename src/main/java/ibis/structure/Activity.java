package ibis.structure;

import gnu.trove.TIntArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.context.UnitActivityContext;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public abstract class Activity extends ibis.constellation.Activity {
  private static final Logger logger = Logger.getLogger(Activity.class);

  /** Parent of this activity. */
  protected ActivityIdentifier parent;
  /** Depth of the search. */
  protected int depth;
  /** Instance to be solved. */
  protected Skeleton instance;

  protected Activity(final ActivityIdentifier parent,
                     final int depth,
                     final Skeleton instance) {
    super(UnitActivityContext.DEFAULT, true);
    this.parent = parent;
    this.depth = depth;
    this.instance = instance;
  }

  protected void reply(final Solution response) {
    executor.send(new Event(identifier(), parent, response));
  }

  protected void verify(final Solution response) {
    verify(response, 0);
  }

  protected void verify(final Solution response, int branch) {
    if (Configure.enableExpensiveChecks) {
      if (response.isSatisfiable()) {
        try {
          verifyUnits(response.units());
          verifySatisfied(response.units());
        } catch (Exception e) {
          logger.error("Verification failed", e);
          logger.error("Units are " + (new TIntArrayList(response.units())));
          logger.error("Branch is " + branch);
          logger.error("Formula is " + instance);
          // System.exit(1);  // TODO: exit gracefully
        }
      }
    }
  }

  /** Checks units don't contain a contradiction */
  private void verifyUnits(final int[] units) throws Exception {
    BitSet unitsSet = new BitSet();
    for (int unit : units) {
      unitsSet.add(unit);
      if (unitsSet.contains(-unit)) {
        throw new Exception("Contradiction unit " + unit);
      }
    }
  }

  /** Checks all CNF clauses are satisfied */
  private void verifySatisfied(final int[] units) throws Exception {
    TIntArrayList formula = instance.formula;
    BitSet unitsSet = new BitSet();
    unitsSet.addAll(units);

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int type = type(formula, clause);

      if (type == OR) {
        boolean satisfied = false;
        for (int i = clause; i < clause + length; i++) {
          if (unitsSet.contains(formula.get(i))) {
            satisfied = true;
            break;
          }
        }
        if (!satisfied) {
          throw new Exception(
              "Unsatisfied clause " + clauseToString(formula, clause));
        }
      } else {
        int numAssigned = 0;
        for (int i = clause; i < clause + length; i++) {
          if (unitsSet.contains(formula.get(i))) {
            numAssigned++;
          }
        }
        if (type == XOR && (numAssigned & 1) == 0) {
          throw new Exception(
              "Unsatisfied clause " + clauseToString(formula, clause));
        }
        if (type == NXOR && (numAssigned & 1) == 1) {
          throw new Exception(
              "Unsatisfied clause " + clauseToString(formula, clause));
        }
      }
    }
  }

  @Override
  public void cleanup() throws Exception {
  }

  @Override
  public void cancel() throws Exception {
  }
}
