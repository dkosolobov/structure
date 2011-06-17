package ibis.structure;

import java.util.Random;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

/**
 * Activity selects a branch for current instance.
 */
public final class SelectBranchActivity extends Activity {
  private static final Logger logger = Logger.getLogger(
      SelectBranchActivity.class);
  private static final Random random = new Random(1);

  public SelectBranchActivity(final ActivityIdentifier parent,
                              final int depth,
                              final long generation,
                              final Skeleton instance) {
    super(parent, depth, generation, instance);
    assert instance.size() > 0;
  }

  @Override
  public void initialize() {
    executor.submit(new BranchActivity(
          parent, depth, generation, instance, chooseBranch()));

    finish();
  }

  /**
   * Chooses a literal for branching.
   *
   * @return literal to branch on
   */
  private int chooseBranch() {
    final int numVariables = instance.numVariables;
    final double[] scores = instance.evaluateLiterals();

    int bestBranch = 0;
    double bestScore = Double.NEGATIVE_INFINITY;

    for (int branch = 1; branch <= numVariables; branch++) {
      double score = instance.evaluateBranch(scores, branch);
      if (score > bestScore && random.nextDouble() < Configure.ttc[3]) {
        bestBranch = branch;
        bestScore = score;
      }
    }

    return random.nextBoolean() ? bestBranch : Misc.neg(bestBranch);
  }
}
