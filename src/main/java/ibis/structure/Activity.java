package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.context.UnitActivityContext;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

/**
 * The base activity for all activities.
 */
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

  /** A normalizer for the instance to be solver. */
  private Normalizer normalizer = null;
  /** Original instance to be solved. */
  private Skeleton original = null;

  /**
   * Creates an activity with default parent, depth, generation
   * and no instance.
   */
  protected Activity() {
    super(UnitActivityContext.DEFAULT, true);
  }

  /**
   * Creates an activity.
   *
   * @param parent parent activity
   * @param depth depth of computation
   * @param generation current restart generation (0, no generation)
   * @param instance instance to be solved.
   */
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
      // Keeps a copy of original instance for verification.
      original = instance.clone();
    }
  }

  /**
   * Sends reponse back to parent.
   *
   * It denormalizes and verifies the response.
   *
   * @param response solution to send to parent
   */
  protected final void reply(final Solution response) {
    if (normalizer != null) {
      normalizer.denormalize(response);
    }

    verify(response);
    executor.send(new Event(identifier(), parent, response));
  }

  /**
   * Normalizes Activity instance.
   *
   * Normaly this should be called before instance is processed.
   */
  protected final void normalize() {
    normalizer = new Normalizer();
    normalizer.normalize(instance);
  }

  /** Performs a garbage collector. */
  protected void gc() {
    instance = null;
  }

  /**
   * Verifies that solution satisfies instance.
   *
   * @param response solution to verify
   * @throws Exception in case of error
   */
  public final void verify(final Solution response) {
    if (Configure.enableExpensiveChecks && original != null) {
      if (response.isSatisfiable()) {
        // For satisfiable instances reponse contains a proof.
        try {
          verifyUnits(response.units());
          verifySatisfied(response.units());
        } catch (Exception e) {
          logger.error("Verification failed", e);
          // logger.info("Failed instance is\n" + original);
          System.exit(1);  // TODO: exit gracefully
        }
      }
    }
  }

  /**
   * Checks units don't contain a contradiction.
   *
   * @param units assignment to verify
   * @throws Exception in case of error.
   */
  public final void verifyUnits(final int[] units) throws Exception {
    TIntArrayList formula = original.formula;
    TIntHashSet unitsSet = new TIntHashSet(units);

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);

      for (int i = clause; i < clause + length; i++) {
        int literal = formula.get(i);
        if (!unitsSet.contains(literal) && !unitsSet.contains(neg(literal))) {
          throw new Exception(
              "Literal " + literal + " in formula, but " + "not assigned.");
        }
      }
    }

    for (int unit : units) {
      if (unitsSet.contains(neg(unit))) {
        throw new Exception("Contradiction unit " + unit);
      }
    }
  }

  /** Checks that all clauses are satisfied.
   *
   * @param units assignment to verify
   * @throws Exception in case of error.
   */
  public final void verifySatisfied(final int[] units) throws Exception {
    TIntArrayList formula = original.formula;
    TIntHashSet unitsSet = new TIntHashSet(units);

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
  public void suspend() {
    gc();
    super.suspend();
  }

  @Override
  public void initialize() throws Exception {
  }

  @Override
  public void process(final Event e) throws Exception {
  }

  @Override
  public void cleanup() throws Exception {
  }

  @Override
  public void cancel() throws Exception {
  }
}
