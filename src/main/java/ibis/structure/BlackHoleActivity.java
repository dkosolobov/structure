package ibis.structure;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.set.hash.TLongHashSet;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

/**
 * This Activity purpose is to remove instances from
 * old generations.
 */
public final class BlackHoleActivity extends Activity {
  private static final Logger logger = Logger.getLogger(
      BlackHoleActivity.class);

  /** A set of dead generations. */
  private static final TLongHashSet graveyard = new TLongHashSet();

  private int branch;

  /** Checks and removes instances from dead generation. */
  public BlackHoleActivity(final ActivityIdentifier parent,
                           final int depth,
                           final long generation,
                           final TDoubleArrayList scores,
                           final Skeleton instance,
                           final int branch) {
    super(parent, depth, generation, scores, instance);
    this.branch = branch;
  }

  /** Sets generation kill as dead. */
  public static void moveToGraveyard(final long kill) {
    synchronized (graveyard) {
      graveyard.add(kill);
    }
  }

  @Override
  public void initialize() {
    boolean dead;
    synchronized (graveyard) {
      dead = graveyard.contains(generation);
    }

    if (dead) {
      reply(Solution.unknown());
      finish();
    } else {
      // logger.info("at " + this + " branching on " + branch);
      executor.submit(new SolveActivity(
            identifier(), depth, generation, scores, instance, branch));
      suspend();
    }
  }

  static int counter = 0;

  @Override
  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    if (response.isUnsatisfiable()) {
      updateScore(scores, depth, branch);
    }

    reply(response);
    finish();
  }
}
