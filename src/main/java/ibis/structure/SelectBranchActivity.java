package ibis.structure;

import java.util.Random;
import java.util.Arrays;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.map.hash.TIntIntHashMap;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

import java.io.*;

/**
 * Activity selects a branch for current instance.
 */
public final class SelectBranchActivity extends Activity {
  private static final Logger logger = Logger.getLogger(
      SelectBranchActivity.class);

  private static final int NUM_VARIABLES = 4;
  private static final Random random = new Random(1);

  /** Selected variables for branching. */
  private int[][] lits = null;  
  /** List of forced literals. */
  private TIntArrayList forced = new TIntArrayList();
  /** Assignments for each literals (2 * num selected variables). */
  private TIntIntHashMap assignment = null;

  private int totalScore;
  private int[] assignmentScore;
  private int branch;

  public SelectBranchActivity(final ActivityIdentifier parent,
                              final int depth,
                              final long generation,
                              final TDoubleArrayList scores,
                              final Skeleton instance) {
    super(parent, depth, generation, scores, instance);
  }

  public static int pass = 0;

  @Override
  public void initialize() {
    if (instance.size() == 0) {
      reply(Solution.satisfiable(new TIntArrayList()));
      finish();
      return;
    }

    Solution solution = null;
    pickVariables();
    initialAssignment();

    try {
      for (int repeat = 4; repeat > 0; repeat --) {
        // Marks all forced literals in assignments.
        for (int i = 0; i < forced.size(); i++) {
          int literal = forced.getQuick(i);
          assignment.put(literal, (1 << (2 * vars.length)) - 1);
          assignment.put(neg(literal), 0);
        }

        int tmp = forced.size();
        propagate();
        findExtraForced();

        if (forced.size() > tmp) {
          repeat++;
        }
      }

      solution = getAssignment();
    } catch (ContradictionException e) {
      solution = Solution.unsatisfiable();
    }

    if (solution != null) {
      SolveActivity.solved += Math.pow(0.5, depth);
      reply(solution);
      finish();
      return;
    }

    for (int i = 0; i < forced.size(); i++) {
      int literal = forced.getQuick(i);
      instance.formula.add(encode(1, OR));
      instance.formula.add(literal);
    }

    int branch = chooseBranch();
    if (forced.contains(branch)) {
      logger.warn("Branching on forced literal " + branch + " " + this);
    }

    executor.submit(new BranchActivity(
          identifier(), depth, generation, scores, instance, branch));

    assignment = null;
    suspend();
  }

  @Override
  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    if (response.isUnknown()) {
      response.learnUnits(forced, 0);
    }

    reply(response);
    finish();
  }

  /** Makes a pass over the formula propagating assigned literals. */
  private void propagate() throws ContradictionException {
    totalScore = 0;
    assignmentScore = new int[2 * vars.length];

    // true if current clause was satisfied
    boolean[] satisfied = new boolean[2 * vars.length];
    // number of literals falsified in current clause
    int[] falsified = new int[2 * vars.length];
    // last unsassigned literal
    int[] unassigned = new int[2 * vars.length];

    ClauseIterator it = new ClauseIterator(instance.formula);
clause_loop:
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(instance.formula, clause);

      totalScore++;
      Arrays.fill(satisfied, false);
      Arrays.fill(falsified, 0);

      for (int i = clause; i < clause + length; i++) {
        int l = instance.formula.getQuick(i);
        int p = assignment.get(l);
        int n = assignment.get(neg(l));

        for (int j = 0; j < 2 * vars.length; j++) {
          int mask = 1 << j;
          if ((n & mask) != 0) {
            falsified[j]++;
          } else if ((p & mask) == 0) {
            unassigned[j] = l;
          } else { // if ((p & mask) != 0) {
            satisfied[j] = true;
          }
        }
      }

      for (int j = 0; j < 2 * vars.length; j++) {
        int literal = literal(j);
        if (falsified[j] == length) {
          forceLiteral(neg(literal));
        }

        if (!satisfied[j] && falsified[j] + 1 == length) {
          // Not satisfied, and all literals except one are unsatisfied.
          // unassigned[j] is forced.
          assert (assignment.get(unassigned[j]) & (1 << j)) == 0;
          assert (assignment.get(neg(unassigned[j])) & (1 << j)) == 0;
          assignment.put(unassigned[j], assignment.get(unassigned[j]) | (1 << j));
          satisfied[j] = true;
        }

        if (!satisfied[j]) {
          assignmentScore[j]++;
        }
      }
    }
  }

  /** Returns propagated literal indexed by j */
  private int literal(final int j) {
    return (j & 1) == 0 ? vars[j / 2] : neg(vars[j / 2]);
  }

  /** Returns a solution if propagation found oune. */
  private Solution getAssignment() {
    int p = -1;
    for (int j = 0; j < 2 * vars.length; j++) {
      if (assignmentScore[j] == 0) {
        p = j;
        break;
      }
    }

    if (p == -1) {
      return null;
    }

    // Gets the assignment.
    TIntHashSet units = new TIntHashSet();
    TIntIntIterator it = assignment.iterator();
    for (int size = assignment.size(); size > 0; size--) {
      it.advance();
      if ((it.value() & (1 << p)) != 0) {
        units.add(it.key());
      }
    }

    // We are required to give value to all variables.
    ClauseIterator it1 = new ClauseIterator(instance.formula);
    while (it1.hasNext()) {
      int clause = it1.next();
      int length = length(instance.formula, clause);

      for (int i = clause; i < clause + length; i++) {
        int literal = instance.formula.getQuick(i);
        if (!units.contains(literal) && !units.contains(neg(literal))) {
          units.add(literal);
        }
      }
    }

    return Solution.satisfiable(units);
  }

  public static int print = 3;

  /** Returns the best branch. */
  private int chooseBranch() {
    double bestScore = Double.NEGATIVE_INFINITY;
    int bestBranch = vars[0];

    for (int i = 0; i < vars.length; i++) {
      if (forced.contains(vars[i]) || forced.contains(neg(vars[i]))) {
        continue;
      }

      double p = 1. * assignmentScore[2 * i + 0] / totalScore;
      double n = 1. * assignmentScore[2 * i + 1] / totalScore;
      double score = (1. + 2 * this.scores.get(vars[i])) / (1024L * n * p + n + p);

      if (score > bestScore) {
        bestScore = score;
        bestBranch = vars[i];
      }
    }

    if (print > 0) {
      print--;
      System.err.println("at " + this + " picked " + bestBranch
                         + " from " + new TIntArrayList(vars));
    }

    return random.nextBoolean() ? bestBranch : Misc.neg(bestBranch);
  }

  /** Finds extra forced literals. */
  private void findExtraForced() throws ContradictionException {
    final int mask = 0x55555555;
    TIntIntIterator it = assignment.iterator();
    for (int size = assignment.size(); size > 0; size--) {
      it.advance();
      int literal = it.key();
      int p = assignment.get(literal);
      if ((p & (p >> 1) & mask) != 0) {
        forceLiteral(literal);
      }
    }
  }

  /** Sets literal when a contradiction is found. */
  private void forceLiteral(final int literal) throws ContradictionException {
    if (forced.contains(neg(literal))) {
      throw new ContradictionException();
    }
    if (!forced.contains(literal)) {
      forced.add(literal);
    }
  }

  /** Sets initial literal assignment. */
  private void initialAssignment() {
    assignment = new TIntIntHashMap();
    for (int i = 0; i < vars.length; i++) {
      assignment.put(vars[i], 1 << (2 * i));
      assignment.put(neg(vars[i]), 1 << (2 * i + 1));
    }
  }

  /** Picks at most NUM_VARIABLES for branching. */
  private void pickVariables() {
    // Scores all variables.
    TIntIntHashMap scores = new TIntIntHashMap();
    ClauseIterator it = new ClauseIterator(instance.formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(instance.formula, clause);
      int delta = 128 >> length;
      int extra = length == 2 ? 8 : 0;

      for (int i = clause; i < clause + length; i++) {
        int literal = instance.formula.getQuick(i);
        scores.adjustOrPutValue(literal, delta, delta);
        scores.adjustOrPutValue(neg(literal), extra, extra);
      }
    }

    int[] top = new int[NUM_VARIABLES];
    long[] cnt = new long[NUM_VARIABLES];
    int num = 0;

    TIntIntIterator it1 = scores.iterator();
    for (int size = scores.size(); size > 0; size--) {
      it1.advance();
      int l = it1.key();

      if (l < 0) {
        continue;
      }

      // A variable's score depends on scores of both phases.
      int p = scores.get(l);
      int n = scores.get(neg(l));
      long c = 1024L * p * n + p + n;

      if (num < top.length) {
        top[num] = l;
        cnt[num] = c;
        num++;
        continue;
      }
      
      if (c <= cnt[top.length - 1]) {
        continue;
      }

      int pos = top.length - 1;
      for (; pos > 0 && c > cnt[pos - 1]; pos--) {
        top[pos] = top[pos - 1];
        cnt[pos] = cnt[pos - 1];
      }

      top[pos] = l;
      cnt[pos] = c;
    }

    vars = new int[NUM_VARIABLES * (NUM_VARIABLES - 1) / 2][];
    for (int i = 0; i 
    
    vars = java.util.Arrays.copyOf(top, num);
    assert vars.length > 0;
  }
}
