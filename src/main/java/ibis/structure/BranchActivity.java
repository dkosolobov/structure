package ibis.structure;

import java.util.Random;
import gnu.trove.TIntArrayList;
import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntHashSet;;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


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

  /**
   * Computes scores for every literal.
   *
   * Building a Hybrid SAT Solver via Conflict Driven, Look Ahead and XOR Reasoning Techniques
   */
  double[] evaluateLiterals() {
    int numVariables = instance.numVariables;
    TIntArrayList formula = instance.formula;
    double[] scores = new double[2 * numVariables + 1];
    ClauseIterator it;

    final double alpha = 0.65;
    final double beta = 0.55;
    final double gamma = 0.63;
    
    it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int type = type(formula, clause);
      double delta;

      if (type == OR) {
        delta = Math.pow(alpha, length);
        for (int i = clause; i < clause + length; i++) {
          int literal = formula.getQuick(i);
          scores[neg(literal) + numVariables] += delta;
        }
      } 

      if (type != OR) {
        delta = Math.pow(beta, length);
        for (int i = clause; i < clause + length; i++) {
          int literal = formula.getQuick(i);
          scores[literal + numVariables] += delta;
          scores[neg(literal) + numVariables] += delta;
        }
      } 
    }

    it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int type = type(formula, clause);

      if (type == OR && length == 2) {
        int u = formula.getQuick(clause);
        int v = formula.getQuick(clause + 1);
        scores[neg(u) + numVariables] += gamma * scores[v + numVariables];
      }
    }

    return scores;
  }

  /** Chooses a literal for branching */
  private int chooseBranch() {
    final int numVariables = instance.numVariables;
    final int maxSize = 8;

    double[] branchScores = evaluateLiterals();
    int[] branches = new int[maxSize];
    double[] scores = new double[maxSize];
    int size = 0;

    for (int branch = 1; branch <= numVariables; branch++) {
      double p = branchScores[branch + numVariables];
      double n = branchScores[-branch + numVariables];
      double score = 1024 * n * p + n + p;

      int j = 0;
      for (; j < size; j++) {
        if (branches[j] == 0 || scores[j] < score) {
          break;
        }
      }
      if (j == branches.length) {
        continue;
      }

      if (branches[j] != 0) {
        for (int k = size - 1; k > j; k--) {
          branches[k] = branches[k - 1];
          scores[k] = scores[k - 1];
        }
      }

      branches[j] = branch;
      scores[j] = score;
      if (j == size) {
        size++;
      }
    }

    int branch = branches[random.nextInt(size)];
    if (random.nextInt(2) == 0) {
      return neg(branch);
    }
    return branch;
  }
}
