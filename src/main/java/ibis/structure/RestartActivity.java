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

  private static Random random = new Random(1);
  private static int delay = 10;
  private Timer timer = new Timer();

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

    logger.info("Spawning generation " + generation
                + " for " + delay + " seconds");
    logger.info("Instance has " + instance.numVariables + " / "
                + instance.formula.size());
    executor.submit(new BlackHoleActivity(
          identifier(), depth, generation, instance.clone()));

    if (delay < 1000000) {
      TimerTask task = new TimerTask() {
        public void run() {
          executor.submit(new BlackHoleActivity(generation));
        }
      };

      timer.schedule(task, delay * 1000L);
      delay += random.nextInt(delay);
    }
  }

  @Override
  public void gc() {
    
  }

  @Override
  public void initialize() {
    startNewGeneration();
    suspend();
  }

  @Override
  public void process(Event e) throws Exception {
    Solution response = (Solution) e.data;

    if (!response.isUnknown()) {
      reply(response);
      finish();
      return;
    }

    // response.show(">> ", 0, 0);
    response.addLearnedClauses(instance.formula);
    executor.submit(new SimplifyActivity(identifier(), depth, instance));
    suspend();
  }
}
