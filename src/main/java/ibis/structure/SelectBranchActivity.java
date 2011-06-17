package ibis.structure;

import java.util.Random;
import gnu.trove.list.array.TDoubleArrayList;
import ibis.constellation.ActivityIdentifier;
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
                              final TDoubleArrayList scores,
                              final Skeleton instance) {
    super(parent, depth, generation, scores, instance);
    assert instance.size() > 0;
  }

  @Override
  public void initialize() {
    normalize();

    executor.submit(new BranchActivity(
          identifier(), depth, generation, scores, instance, chooseBranch()));

    suspend();
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
      if (score > bestScore) {
        bestBranch = branch;
        bestScore = score;
      }
    }

    return random.nextBoolean() ? bestBranch : Misc.neg(bestBranch);
  }
}
