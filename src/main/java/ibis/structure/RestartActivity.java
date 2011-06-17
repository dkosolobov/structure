package ibis.structure;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;
import gnu.trove.list.array.TDoubleArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import gnu.trove.list.array.TIntArrayList;

/**
 * Implements restarting strategy.
 *
 * Restarting loop contains a few preprocessing activities:
 * BCE, VE, simplification.
 *
 * Each solved instance is part of a generation. When a timer
 * expires is generation killed (see BlackHoleActivity).
 *
 * When a generation is finished RestartActivity
 * adds to formula learned clauses.
 *
 * KNOWN BUGS: learned clauses "enforce" the variable ordering
 * for the future generations.
 */
public final class RestartActivity extends Activity {
  private static final int INITIAL_TTL = 15;
  private static final int MAX_TTL = 1000000;
  private static final int DEPTH = Integer.MAX_VALUE;

  private static final Logger logger = Logger.getLogger(RestartActivity.class);
  private static final Random random = new Random(1);

  /** Current generation time to live. */
  private static int ttl = INITIAL_TTL;  // TODO: should not be static
  /** The original instance. */
  private Skeleton original;

  public RestartActivity(final ActivityIdentifier parent,
                         final TDoubleArrayList scores,
                         final Skeleton instance) {
    super(parent, 0, 0, scores, instance);
    original = instance;
  }

  /**
   * Returns a unique random long non-zero number.
   *
   * @return an unique id.
   */
  private static long guid() {
    long generation;
    do {
      generation = random.nextLong();
    } while (generation == 0);
    return generation;
  }

  @Override
  public void initialize() {
    final long generation = guid();

    logger.info("Spawning generation " + generation + " for "
                + ttl + " seconds");
    logger.info("Instance has " + instance.numVariables + " / "
                + instance.formula.size());

    executor.submit(new BlackHoleActivity(
          identifier(), DEPTH, generation, scores, original.clone()));

    if (ttl < MAX_TTL) {
      TimerTask task = new TimerTask() {
        public void run() {
          BlackHoleActivity.moveToGraveyard(generation);
        }
      };

      (new Timer()).schedule(task, ttl * 1000L);
      ttl += random.nextInt(5);
    }

    suspend();
  }

  @Override
  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;

    if (!response.isUnknown()) {
      reply(response);
      finish();
      return;
    }

    response.addLearnedClauses(original.formula);
    executor.submit(new BlockedClauseEliminationActivity(
          identifier(), scores, original));
    suspend();
  }
}
