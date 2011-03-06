package ibis.structure;

import java.util.Random;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class SelectBranchActivity extends Activity {
  private static final Logger logger = Logger.getLogger(SelectBranchActivity.class);
  private static final Random random = new Random(1);

  private InstanceNormalizer normalizer = new InstanceNormalizer();

  public SelectBranchActivity(final ActivityIdentifier parent,
                              final int depth,
                              final Skeleton instance,
                              final boolean learn) {
    super(parent, depth, instance, learn);
    assert instance.size() > 0;
  }

  public void initialize() {
    normalizer.normalize(instance);
    int branch = chooseBranch();
    
    executor.submit(new BranchActivity(
          identifier(), depth, instance, learn, branch));

    gc();
    suspend();
  }

  public void process(Event e) throws Exception {
    Solution response = (Solution) e.data;
    if (response.isSatisfiable()) {
      verify(response);
      normalizer.denormalize(response);
    }

    reply(response);
    finish();
  }

  /** Chooses a literal for branching */
  private int chooseBranch() {
    final int numVariables = instance.numVariables;
    final double[] scores = instance.evaluateLiterals();

    int bestBranch = 0;
    double bestScore = Double.NEGATIVE_INFINITY;;

    for (int branch = 1; branch <= numVariables; branch++) {
      double score = instance.evaluateBranch(scores, branch);
      if (score > bestScore) {
        bestBranch = branch;
        bestScore = score;
      }
    }

    return random.nextBoolean() ? bestBranch : neg(bestBranch);
  }
}
