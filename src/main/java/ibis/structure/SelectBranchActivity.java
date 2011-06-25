package ibis.structure;

import java.util.Random;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import ibis.constellation.ActivityIdentifier;
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
                              final int depth,
                              final long generation,
                              final TDoubleArrayList scores,
                              final Skeleton instance) {
    super(parent, depth, generation, scores, instance);
  }

  @Override
  public void initialize() {
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

    TIntHashSet tmp = new TIntHashSet();
    ClauseIterator it = new ClauseIterator(instance.formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(instance.formula, clause);
      for (int i = clause; i < clause + length; i++) {
        tmp.add(var(instance.formula.getQuick(i)));
      }
    }

    int bestBranch = 0;
    double bestScore = Double.NEGATIVE_INFINITY;

    // TODO: it is possible to choose a branch that's not present
    TIntIterator it1 = tmp.iterator();
    for (int size = tmp.size(); size > 0; size--) {
      int branch = it1.next();

      double score = 1.;
      score *= evaluateBranch(numVariables, scores, branch);
      score *= this.scores.get(branch);

      if (score > bestScore) {
        bestBranch = branch;
        bestScore = score;
      }
    }

    return random.nextBoolean() ? bestBranch : Misc.neg(bestBranch);
  }

  /**
   * Returns a branching score for a variable
   * knowing scores for the two phases.
   */
  private static double evaluateBranch(final int numVariables,
                                       final double[] scores,
                                       final int branch) {
    double p = scores[branch + numVariables];
    double n = scores[neg(branch) + numVariables];
    return 1024 * n * p + n + p;
  }
}
