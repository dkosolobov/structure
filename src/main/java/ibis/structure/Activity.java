package ibis.structure;

import gnu.trove.TIntArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.context.UnitActivityContext;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

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
    if (Configure.enableExpensiveChecks) {
      if (response.isSatisfiable()) {
        try {
          verifyInternal(response.units());
        } catch (Exception e) {
          logger.error("Verification failed", e);
          logger.error("Units are " + (new TIntArrayList(response.units())));
          logger.error("Instance is " + instance);
          System.exit(1);  // TODO: exit gracefully
        }
      }
    }
  }

  /** Verify correctness of solution. */
  private void verifyInternal(final int[] units) throws Exception {
    // Checks units don't contain a contradiction
    BitSet unitsSet = new BitSet();
    for (int unit : units) {
      unitsSet.add(unit);
      if (unitsSet.contains(-unit)) {
        throw new Exception("Contradiction unit " + unit);
      }
    }

    // Checks all clauses are satisfied
    boolean satisfied = false;
    int lastStart = 0;
    for (int i = 0; i < instance.clauses.size(); ++i) {
      int literal = instance.clauses.get(i);
      if (literal == 0) {
        if (!satisfied) {
          TIntArrayList clause = instance.clauses.subList(lastStart, i);
          throw new Exception("Unsatisfied clause " + clause);
        }
        lastStart = i + 1;
        satisfied = false;
      }
      if (unitsSet.contains(literal)) {
        satisfied = true;
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
