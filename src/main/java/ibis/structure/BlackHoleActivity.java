package ibis.structure;

import gnu.trove.set.hash.TLongHashSet;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/**
 * TODO: Handle case when all variables are eliminated.
 */
public final class BlackHoleActivity extends Activity {
  private static final Logger logger = Logger.getLogger(
      BlackHoleActivity.class);

  private static final TLongHashSet graveyard = new TLongHashSet();

  public long kill = 0L;

  public BlackHoleActivity(final long kill) {
    super();
    assert kill != 0L;
    this.kill = kill;
    moveToGraveyard(kill);
  }

  public BlackHoleActivity(final ActivityIdentifier parent,
                           final int depth,
                           final long generation,
                           final Skeleton instance) {
    super(parent, depth, generation, instance);
    assert instance.size() > 0;
  }

  private static void moveToGraveyard(final long kill) {
    synchronized (graveyard) {
      graveyard.add(kill);
    }
  }

  public void initialize() {
    if (kill == 0) {
      boolean dead;
      synchronized (graveyard) {
        dead = graveyard.contains(generation);
      }

      if (dead) {
        reply(Solution.unknown());
      } else {
        executor.submit(new SplitActivity(
              parent, depth, generation, instance));
      }

      finish(); 
    } else {
      moveToGraveyard(kill);
      finish();
    }
  }

  public void process(Event e) throws Exception {
  }
}
