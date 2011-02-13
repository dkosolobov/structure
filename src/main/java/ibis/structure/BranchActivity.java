package ibis.structure;

import java.util.Random;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;


public final class BranchActivity extends Activity {
  private static final Logger logger = Logger.getLogger(SplitActivity.class);
  private static final Random random = new Random(1);

  private InstanceNormalizer normalizer = new InstanceNormalizer();
  /** Responses from branches */
  private Solution[] responses = new Solution[2];
  /** Number of responses received */
  private int numReplies = 0;

  public BranchActivity(final ActivityIdentifier parent,
                        final int depth,
                        final Skeleton instance,
                        final Vitality vitality) {
    super(parent, depth, instance, vitality);
  }

  public void initialize() {
    normalizer.normalize(instance);
    vitality.normalize(normalizer.literalMap());

    int branch = chooseBranch();
    assert branch != 0;

    executor.submit(new SolveActivity(identifier(), depth - 1, instance,
                                      new Vitality(vitality), branch));
    executor.submit(new SolveActivity(identifier(), depth - 1, instance, 
                                      new Vitality(vitality), -branch));
    suspend();
  }

  public void process(Event e) throws Exception {
    Solution response = (Solution)e.data;
    if (response.isSatisfiable()) {
      if (numReplies == 0 || !responses[0].isSatisfiable()) {
        // Sends the solution to parent.
        verify(response);
        normalizer.denormalize(response);
        reply(response);
      }
    }

    responses[numReplies] = response;
    numReplies++;

    if (numReplies == 1) {
      // Waits for the other branch to finish.
      suspend();
      return;
    }

    // Both branches finished
    assert numReplies == 2;
    if (responses[0].isSatisfiable() || responses[1].isSatisfiable()) {
      // A solution was already found and sent to parent.
    } else if (responses[0].isUnsatisfiable() && responses[1].isUnsatisfiable()) {
      // Both braches returned UNSATISFIABLE so the instance is unsatifiable
      reply(Solution.unsatisfiable());
    } else {
      reply(Solution.unknown());
    }
    finish();
  }

  private int chooseBranch() {
    final int tournamentSize = 16;

    int bestBranch = 0;
    double bestValue = Double.NEGATIVE_INFINITY;

    for (int i = 0; i < tournamentSize; i++) {
      int branch = 1 + random.nextInt(instance.numVariables);
      double value = vitality.vitality(branch);
      if (value > bestValue) {
        bestBranch = branch;
        bestValue = value;
      }
    }

    return bestBranch;
  }
}
