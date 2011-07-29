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
  private static TLongHashSet graveyard = new TLongHashSet();
  /** Branch (for SolveActivity). */
  private int branch;

  /** Checks and removes instances from dead generation. */
  public BlackHoleActivity(final ActivityIdentifier parent,
                           final ActivityIdentifier tracer,
                           final int depth,
                           final long generation,
                           final TDoubleArrayList scores,
                           final Skeleton instance,
                           final int branch) {
    super(parent, tracer, depth, generation, scores, instance);
    this.branch = branch;
  }

  /** Sets generation kill as dead. */
  public static void killGeneration(final long kill) {
    synchronized (BlackHoleActivity.class) {
      graveyard.add(kill);
    }
  }

  @Override
  public void initialize() {
    TracerSlave.registerSlave(tracer);

    boolean dead;
    synchronized (BlackHoleActivity.class) {
      dead = graveyard.contains(generation);
    }

    if (dead) {
      reply(Solution.unknown());
      finish();
    } else {
      executor.submit(new SolveActivity(
            identifier(), tracer, depth, generation, scores, instance, branch));
      suspend();
    }
  }

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
