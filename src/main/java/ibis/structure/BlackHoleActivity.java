package ibis.structure;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.set.hash.TLongHashSet;
import ibis.constellation.ActivityIdentifier;
import org.apache.log4j.Logger;

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

    if (depth <= 0 || dead) {
      reply(Solution.unknown());
    } else {
      executor.submit(new SolveActivity(
            parent, depth, generation, scores, instance, branch));
    }

    finish();
  }
}
