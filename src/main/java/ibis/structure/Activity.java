package ibis.structure;

import java.io.*;

import gnu.trove.list.array.TDoubleArrayList;
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
  protected static final Logger logger = Logger.getLogger(Activity.class);

  /** Parent of this activity. */
  protected ActivityIdentifier parent = null;
  /** Depth of the search. */
  protected int depth = Integer.MAX_VALUE;
  /** Instance generation. */
  protected long generation = 0;
  /** Variables scores. */
  protected TDoubleArrayList scores = null;
  /** Instance to be solved. */
  protected Skeleton instance = null;
  /** Original instance to be solved. */
  protected Skeleton original = null;
  /** True if activity was already replied. */
  private boolean replied = false;

  /**
   * Creates an activity.
   *
   * @param parent parent activity
   * @param depth depth of computation
   * @param generation current restart generation (0, no generation)
   * @param scores variables scores
   * @param instance instance to be solved
   */
  protected Activity(final ActivityIdentifier parent,
                     final int depth,
                     final long generation,
                     final TDoubleArrayList scores,
                     final Skeleton instance) {
    super(UnitActivityContext.DEFAULT, true);
    this.parent = parent;
    this.depth = depth;
    this.generation = generation;
    this.scores = scores;
    this.instance = instance;

    if (Configure.enableExpensiveChecks) {
      verify(instance);
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
    assert !replied : "Already replied";
    replied = true;

    verify(response);
    executor.send(new Event(identifier(), parent, response));
  }

  /** Performs a garbage collector. */
  protected void gc() {
    // instance = null;
  }

  /**
   * Verifies an instance.
   *
   * @param instance to be verified.
   */
  public final void verify(final Skeleton instance) {
    if (Configure.enableExpensiveChecks) {
      try {
        verifyInstance(instance);
      } catch (Exception e) {
        logger.error("Verification failed", e);
        // logger.info("Failed instance is\n" + original);
        System.exit(1);  // TODO: exit gracefully
      }
    }
  }

  /**
   * Verifies an instance.
   *
   * @param instance to be verified.
   */
  public final void verifyInstance(final Skeleton instance) throws Exception {
    TIntArrayList formula = instance.formula;
    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);

      for (int i = clause; i < clause + length; i++) {
        int literal = formula.get(i);
        if (1 > var(literal) || var(literal) > instance.numVariables) {
          throw new Exception("Found literal " + literal + ", but "
                              + "numVariables is " + instance.numVariables);
        }
      }
    }
  }

  /**
   * Verifies that solution satisfies instance.
   *
   * @param response solution to verify
   * @throws Exception in case of error
   */
  public final void verify(final Solution response) {
    if (Configure.enableExpensiveChecks && original != null) {
      try {
        response.verifyLearned();
        if (response.isSatisfiable()) {
          // For satisfiable instances reponse contains a proof.
          verifyUnits(response.units());
          verifySatisfied(response.units());
        }
      } catch (Exception e) {
        logger.error("Verification failed", e);
        // logger.info("Failed instance is\n" + original);
        System.exit(1);  // TODO: exit gracefully
      }

      /*
      if (response.isUnsatisfiable()) {
        // For unsatisfiable invoke cryptominisat.
        String cnf = original.toString();
        int r = 0;

        try {
          FileWriter fstream = new FileWriter("out.txt");
          BufferedWriter out = new BufferedWriter(fstream);
          out.write(cnf);
          out.close();
                
          // System.err.println("cnf is " + cnf);
          Process p = Runtime.getRuntime().exec("../cryptominisat /dev/stdin /dev/null");
          p.getOutputStream().write(cnf.getBytes());
          p.getOutputStream().flush();
          p.getOutputStream().close();
          r = p.waitFor();
        } catch (Exception e) {
          logger.error("failed to run cryptoministat");
          e.printStackTrace();
          // ignored
        }

        if (r == 10) {
          try {
            throw new Exception();
          } catch (Exception e) {
            e.printStackTrace();
          }
          logger.info("Satisfiable instance\n");
          System.exit(1);
        }
      }
      */
    }
  }

  /**
   * Checks units don't contain a contradiction.
   *
   * @param units assignment to verify
   * @throws Exception in case of error.
   */
  public final void verifyUnits(final TIntArrayList units) throws Exception {
    TIntArrayList formula = original.formula;
    TIntHashSet unitsSet = new TIntHashSet(units);
    TIntHashSet available = new TIntHashSet();

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);

      for (int i = clause; i < clause + length; i++) {
        int literal = formula.get(i);
        available.add(var(literal));

        if (!unitsSet.contains(literal) && !unitsSet.contains(neg(literal))) {
          throw new Exception(
              "Literal " + literal + " in formula, but " + "not assigned.");
        }
      }
    }

    for (int i = 0; i < units.size(); i++) {
      int unit = units.getQuick(i);
      if (!available.contains(var(unit))) {
        throw new Exception("Unit " + unit + " assigned, but not in formula");
      }
      if (unitsSet.contains(neg(unit))) {
        throw new Exception("Contradictory unit " + unit);
      }
    }
  }

  /** Checks that all clauses are satisfied.
   *
   * @param units assignment to verify
   * @throws Exception in case of error.
   */
  public final void verifySatisfied(final TIntArrayList units) throws Exception {
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
  public String toString() {
    return "(" + instance.size() + " / " + depth + ")";
  }

  @Override
  public void suspend() {
    gc();
    super.suspend();
  }

  @Override
  public void finish() {
    super.finish();
  }

  @Override
  public void initialize() throws Exception {
  }

  @Override
  public void process(final Event e) throws Exception {
    reply((Solution) e.data);
    finish();
  }

  @Override
  public void cleanup() throws Exception {
  }

  @Override
  public void cancel() throws Exception {
  }
}
