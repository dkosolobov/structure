package ibis.structure;

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

  /** Checks and removes instances from dead generation. */
  public BlackHoleActivity(final ActivityIdentifier parent,
                           final int depth,
                           final long generation,
                           final Skeleton instance) {
    super(parent, depth, generation, instance);
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
      executor.submit(new SplitActivity(parent, depth, generation, instance));
    }

    finish();
  }
}
