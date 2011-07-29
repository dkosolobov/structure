package ibis.structure;

import java.util.Random;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TDoubleArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

/**
 * Activity selects a branch for current instance.
 */
public final class SelectBranchActivity extends Activity {
  private static final Logger logger = Logger.getLogger(
      SelectBranchActivity.class);

  private static final Random random = new Random(1);

  public SelectBranchActivity(final ActivityIdentifier parent,
                              final ActivityIdentifier tracer,
                              final int depth,
                              final long generation,
                              final TDoubleArrayList scores,
                              final Skeleton instance) {
    super(parent, tracer, depth, generation, scores, instance);
  }

  @Override
  public void initialize() {
    int branch = instance.pickVariables(scores, 1)[0];
    double score = scores.get(branch);
    branch = random.nextDouble() < score ? branch : neg(branch);

    executor.submit(new BranchActivity(
          parent, tracer, depth, generation, scores, instance, branch));

    finish();
    return;
  }
}
