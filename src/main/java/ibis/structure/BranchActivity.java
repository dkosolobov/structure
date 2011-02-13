package ibis.structure;

import java.util.Random;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;;
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
                        final Skeleton instance) {
    super(parent, depth, instance);
  }

  public void initialize() {
    normalizer.normalize(instance);
    int branch = chooseBranch();
    
    executor.submit(
        new SolveActivity(identifier(), depth - 1, instance, branch));
    executor.submit(
        new SolveActivity(identifier(), depth - 1, instance, -branch));
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

  /** Computes score given number of clauses. */
  private double score(int[] numClauses) {
    double score = 0.;
    double alpha = 1.;
    for (int i = 0; i < numClauses.length; i++) {
      score += alpha * numClauses[i];
      alpha *= 0.618033989;
    }
    return score;
  }

  /** Computes scores for every literal */
  double[] evaluateLiterals() {
    final int maxClauseLength = 16;
    final int numVariables = instance.numVariables;
    final TIntArrayList clauses = instance.clauses;

    int[][] counts = new int[2 * numVariables + 1][];
    for (int u = -numVariables; u <= numVariables; u++) {
      counts[u + numVariables] = new int[maxClauseLength];
    }

    for (int start = 0, end = 0; end < clauses.size(); end++) {
      int u = clauses.get(end);
      if (u == 0) {
        int length = Math.min(end - start, maxClauseLength - 1);
        for (int i = start; i < end; i++) {
          int v = clauses.get(i);
          counts[v + numVariables][length]++;
        }
        start = end + 1;
      }
    }

    double[] scores = new double[2 * numVariables + 1];
    for (int u = -numVariables; u <= numVariables; u++) {
      scores[u + numVariables] = score(counts[u + numVariables]);
    }

    return scores;
  }

  /** Chooses a literal for branching */
  private int chooseBranch() {
    final int numVariables = instance.numVariables;
    final int tournamentSize = 128;

    int bestBranch = 0;
    double bestScore = Double.NEGATIVE_INFINITY;
    double[] scores = evaluateLiterals();
    
    for (int i = 0; i < tournamentSize; i++) {
      int branch = random.nextInt(numVariables) + 1;
      double p = scores[branch + numVariables];
      double n = scores[-branch + numVariables];
      double score = Math.min(p, n);

      if (bestScore < score) {
        bestScore = score;
        bestBranch = branch;
      }
    }

    assert bestBranch != 0;
    return bestBranch;
  }
}
