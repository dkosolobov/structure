package ibis.structure;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class RestartActivity extends Activity {
  private static final Logger logger = Logger.getLogger(RestartActivity.class);

  private Random random = new Random();
  private Timer timer = new Timer();
  private int delay = 10;

  public RestartActivity(final ActivityIdentifier parent,
                         final int depth,
                         final Skeleton instance) {
    super(parent, depth, 0, instance);
    assert instance.size() > 0;
  }

  private long guid() {
    long generation;
    do {
      generation = random.nextLong();
    } while (generation == 0);
    return generation;
  }

  private void startNewGeneration() {
    final long generation = guid();
    
    logger.info("Spawining generation " + generation
                + " for " + delay + " seconds");
    executor.submit(new BlackHoleActivity(
          identifier(), depth, generation, instance.clone()));

    if (delay < 1000000) {
      TimerTask task = new TimerTask() {
        public void run() {
          executor.submit(new BlackHoleActivity(generation));
        }
      };

      timer.schedule(task, delay * 1000L);
      delay = delay + random.nextInt(delay);
    }
  }

  public void gc() {
    
  }

  public void initialize() {
    startNewGeneration();
    suspend();
  }

  public void process(Event e) throws Exception {
    Solution response = (Solution) e.data;

    if (response.isUnknown()) {
      startNewGeneration();
      suspend();
    } else {
      reply(response);
      finish();
    }
  }
}
