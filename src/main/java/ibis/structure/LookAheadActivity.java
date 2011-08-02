package ibis.structure;

import java.util.Random;
import java.util.Arrays;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntLongIterator;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntLongHashMap;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

import java.io.*;

/**
 * Activity selects a branch for current instance.
 */
public final class LookAheadActivity extends Activity {
  private static final Random random = new Random(1);

  /** Number of chunkgs to be received. */
  private int numChunks = 0;
  /** Learned clauses. */  
  private Solution solution = null;
  /** True if solved. */
  private boolean solved = false;


  public LookAheadActivity(final ActivityIdentifier parent,
                           final ActivityIdentifier tracer,
                           final TDoubleArrayList scores,
                           final Skeleton instance) {
    super(parent, tracer, 0, 0, scores, instance);
    this.solution = Solution.unknown();
  }

  protected void gc() {
  }

  @Override
  public void initialize() {
    final int step = 16;
    final int total = step * Configure.lookAheadSize;

    int[] vars = instance.pickVariables(scores, total);
    shuffleArray(vars);

    logger.info("Tried " + total + " variables");
    logger.info("Picked " + new TIntArrayList(vars));
    for (int i = 0; i < vars.length; i += step) {
      int num = Math.min(step, vars.length - i);
      int[] send = Arrays.copyOfRange(vars, i, i + num);
      executor.submit(new PropagateActivity(
            identifier(), tracer, instance, send));
      numChunks++;
    }

    if (numChunks == 0) {
      executor.submit(new SimplifyActivity(parent, tracer, scores, instance));
      finish();
    } else {
      suspend();
    }
  }

  @Override
  public void process(final Event e) {
    Solution response = (Solution) e.data;

    if (!solved) {
      if (!response.isUnknown()) {
        solved = true;
        reply(response);
      } else {
        // Merges learned clauses.
        solution = Solution.unknown(solution, response, false);
      }
    }

    numChunks--;
    assert numChunks >= 0;
    if (numChunks == 0) {
      if (!solved) {
        TIntArrayList learned = new TIntArrayList();
        solution.addLearnedClauses(learned, 10);
        instance.formula.addAll(learned);
        executor.submit(new SimplifyActivity(parent, tracer, scores, instance));
      }

      finish();
    } else {
      suspend();
    }
  }

  /** Shuffles elements of array a. */
  private static void shuffleArray(final int[] a) {
    for (int i = 1; i < a.length; i++) {
      int j = random.nextInt(i);
      int temp = a[i];
      a[i] = a[j];
      a[j] = temp;
    }
  }
}
