package ibis.structure;

import java.util.Random;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class BranchActivity extends Activity {
  private static final Logger logger = Logger.getLogger(SplitActivity.class);
  private final Random random = new Random(1);

  private InstanceNormalizer normalizer = new InstanceNormalizer();
  /** Responses from branches */
  private Solution[] responses = new Solution[2];
  /** Number of responses received */
  private int numReplies = 0;

  public BranchActivity(final ActivityIdentifier parent,
                        final int depth,
                        final Skeleton instance) {
    super(parent, depth, instance);
    assert !instance.formula.isEmpty();
  }

  public void initialize() {
    normalizer.normalize(instance);
    int branch = chooseBranch();
    
    executor.submit(
        new SolveActivity(identifier(), depth - 1, instance, branch));
    executor.submit(
        new SolveActivity(identifier(), depth - 1, instance, -branch));

    if (!Configure.enableExpensiveChecks) {
      instance = null;  // Helps GC
    }
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

  /** Computes scores for every literal */
  /*
   *
   * Building a Hybrid SAT Solver via Conflict Driven, Look Ahead and XOR Reasoning Techniques
   */
  double[] evaluateLiterals() {
    int numVariables = instance.numVariables;
    TIntArrayList formula = instance.formula;
    double[] scores = new double[2 * numVariables + 1];

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int type = type(formula, clause);

      if (type == OR && length >= 2) {
        double delta = Math.pow(0.22, length - 2);
        for (int i = clause; i < clause + length; i++) {
          scores[formula.getQuick(i) + numVariables] += delta;
        }
      } 

      if (type != OR && length >= 2) {
        double delta = 5.5 * Math.pow(0.50, length - 2);
        for (int i = clause; i < clause + length; i++) {
          scores[formula.getQuick(i) + numVariables] += delta;
          scores[neg(formula.getQuick(i)) + numVariables] += delta;
        }
      }
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
