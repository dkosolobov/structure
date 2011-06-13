package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.context.UnitActivityContext;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public class Activity extends ibis.constellation.Activity {
  private static final Logger logger = Logger.getLogger(Activity.class);

  /** Parent of this activity. */
  protected ActivityIdentifier parent = null;
  /** Depth of the search. */
  protected int depth = Integer.MAX_VALUE;
  /** Instance generation. */
  protected long generation = 0;
  /** Instance to be solved. */
  protected Skeleton instance = null;
  /** Original instance to be solved. */
  protected Skeleton original = null;

  protected Activity() {
    super(UnitActivityContext.DEFAULT, true);
  }

  protected Activity(final ActivityIdentifier parent,
                     final int depth,
                     final long generation,
                     final Skeleton instance) {
    super(UnitActivityContext.DEFAULT, true);
    this.parent = parent;
    this.depth = depth;
    this.generation = generation;
    this.instance = instance;

    if (Configure.enableExpensiveChecks) {
      original = instance.clone();
    }
  }

  protected void reply(final Solution response) {
    verify(response);
    executor.send(new Event(identifier(), parent, response));
  }

  public void verify(final Solution response) {
    if (Configure.enableExpensiveChecks && original != null) {
      if (response.isSatisfiable()) {
        // For satisfiable originals reponse contains a proof.
        try {
          verifyUnits(response.units());
          verifySatisfied(response.units());
        } catch (Exception e) {
          logger.error("Verification failed", e);
          logger.info("Failed instance is\n" + original);
          System.exit(1);  // TODO: exit gracefully
        }
      }
    }
  }

  /** Checks units don't contain a contradiction */
  public void verifyUnits(final int[] units) throws Exception {
    BitSet unitsSet = new BitSet();
    for (int unit : units) {
      unitsSet.add(unit);
      if (unitsSet.contains(-unit)) {
        throw new Exception("Contradiction unit " + unit);
      }
    }
  }

  /** Checks all CNF clauses are satisfied */
  public void verifySatisfied(final int[] units) throws Exception {
    TIntArrayList formula = original.formula;
    TIntHashSet unitsSet = new TIntHashSet();
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
  
  protected void gc() {
    instance = null;
  }

  @Override
  public void suspend() {
    gc();
    super.suspend();
  }

  @Override
  public void initialize() throws Exception {
  }

  @Override
  public void process(Event e) throws Exception {
  }

  @Override
  public void cleanup() throws Exception {
  }

  @Override
  public void cancel() throws Exception {
  }
}
