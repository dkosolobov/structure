package ibis.structure;

import java.util.Random;
import java.util.Arrays;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntLongIterator;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntLongHashMap;
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

  private static final int NUM_VARIABLES_ROOT = 15;
  private static final int NUM_VARIABLES = 2;
  private static final Random random = new Random(1);

  public static int print = 0;

  /** Selected variables for branching. */
  private int[] vars = null;
  /** Literals in each propagation. */
  private int[] lits = null;  
  /** True if propagation is contradiction. */
  private boolean[] contradiction = null;
  /** List of forced literals. */
  private TIntArrayList forced = new TIntArrayList();
  /** Assignments for each literals (2 * num selected variables). */
  private TIntIntHashMap assignment = null;

  /** Total number of clauses. */
  private int numClauses = 0;
  /** Number of unsatisfied clauses for each propagation. */
  private int[] numUnsatisfied = null;
  /** Selected branch. */
  private int branch = 0;
  /** Learned clauses. */
  private TIntArrayList learned = new TIntArrayList();

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
    if (vars.length == 1) {
      branch = random.nextBoolean() ? vars[0] : neg(vars[0]);
      executor.submit(new BranchActivity(
            identifier(), depth, generation, scores, instance, branch));
      suspend();
      return;
    }

    try {
      initialAssignment();
      for (int repeat = 5; repeat > 0; repeat--) {
        int tmp = forced.size();
        propagate();
        findExtraContradictions();
        findExtraForced();
        checkContradiction();
        repeat += forced.size() > tmp + 1 ? 1 : 0;
      }

      findExtraContradictions();
      checkContradiction();
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

    updateScores();
    chooseBranch();
    addForcedLiterals();
    print--;

    executor.submit(new BranchActivity(
          identifier(), depth, generation, scores, instance, branch));

    assignment = null;
    suspend();
  }

  @Override
  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    if (response.isUnknown()) {
      response.learnClauses(learned);
    }

    reply(response);
    finish();
  }

  /** Makes a pass over the formula propagating assigned literals. */
  private void propagate() throws ContradictionException {
    numClauses = 0;
    numUnsatisfied = new int[lits.length];

    // true if current clause was satisfied
    boolean[] satisfied = new boolean[lits.length];
    // number of literals falsified in current clause
    int[] falsified = new int[lits.length];
    // last unsassigned literal
    int[] unassigned = new int[lits.length];

    ClauseIterator it = new ClauseIterator(instance.formula);
clause_loop:
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(instance.formula, clause);

      numClauses += 1 + (64 >> length);
      Arrays.fill(satisfied, false);
      Arrays.fill(falsified, 0);

      for (int i = clause; i < clause + length; i++) {
        int l = instance.formula.getQuick(i);
        int p = assignment.get(l);
        int n = assignment.get(neg(l));

        for (int j = 0; j < lits.length; j++) {
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

      for (int j = 0; j < lits.length; j++) {
        if (falsified[j] == length) {
          contradiction[j] = true;
        }

        if (!satisfied[j] && falsified[j] + 1 == length) {
          // Not satisfied, but all literals except one are unsatisfied.
          // The remaining unassigned[j] is constrained.
          assign(unassigned[j], j);
          satisfied[j] = true;
        }

        if (!satisfied[j]) {
          numUnsatisfied[j] += 1 + (64 >> (length - falsified[j]));
        }
      }
    }
  }

  /** Returns a solution if propagation found oune. */
  private Solution getAssignment() {
    int p = -1;
    for (int j = 0; j < lits.length; j++) {
      if (!contradiction[j] && numUnsatisfied[j] == 0) {
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


  /** Returns the best branch. */
  private void chooseBranch() {
    double[] scores = new double[vars.length];
    for (int i = 0; i < vars.length; i++) {
      scores[i] = 1. + 3. * this.scores.get(vars[i]);
      scores[i] *= 1. * numClauses /  numUnsatisfied[2 * i + 0];
      scores[i] *= 1. * numClauses /  numUnsatisfied[2 * i + 1];
    }

    int bestBranch = vars[0];
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < vars.length; i++) {
      if (scores[i] > bestScore
          && !contradiction[2 * i + 0]
          && !contradiction[2 * i + 1]) {
        bestScore = scores[i];
        bestBranch = vars[i];
      }
    }

    if (print > 0) {
      logger.info("At " + this + " picked " + bestBranch + " ("
                  + bestScore + " / " + this.scores.get(var(bestBranch))
                  + ") from " + new TIntArrayList(vars));
    }

    branch = random.nextBoolean() ? bestBranch : neg(bestBranch);
  }

  /** Sets initial literal assignment. */
  private void initialAssignment() {
    assignment = new TIntIntHashMap();
    for (int i = 0; i < lits.length; i++) {
      assign(lits[i], i);
    }
  }

  /** Assigns literal in propagation index. */
  private void assign(final int literal, final int index) {
    assignment.put(literal, assignment.get(literal) | (1 << index));
  }
  
  /** Finds some more contradictions */
  private void findExtraContradictions() {
    // a -> c
    // a -> -c
    // => a -> contradiction
    TIntIntIterator it = assignment.iterator();
    for (int size = assignment.size(); size > 0; size--) {
      it.advance();
      int l = it.key();
      long p = assignment.get(l);
      long n = assignment.get(neg(l));

      if ((p & n) != 0) {
        for (int j = 0; j < lits.length; j++) {
          if ((p & n & (1 << j)) != 0) {
            contradiction[j] = true;
          }
        }
      }
    }
  }

  private void findExtraForced() {
    // In a contradiction any assignment can be assumed
    long any = 0;
    for (int i = 0; i < lits.length; i++) {
      if (contradiction[i]) {
        any |= 1L << i;
      }
    }

    final int mask5 = 0x55555555;
    TIntIntIterator it = assignment.iterator();
    for (int size = assignment.size(); size > 0; size--) {
      it.advance();
      int l = it.key();
      long p = it.value() | any;

      // a -> c
      // -a -> c
      // => c
      if ((p & (p >> 1) & mask5) != 0) {
        forced.add(l);
        continue;
      }
    }

    // a -> contradiction
    // => neg(a)
    for (int i = 0; i < lits.length; i++) {
      if (contradiction[i]) {
        forced.add(neg(lits[i]));
      }
    }

    forced = new TIntArrayList(new TIntHashSet(forced));
    for (int i = 0; i < forced.size(); i++) {
      int literal = forced.get(i);
      assignment.put(literal, (1 << lits.length) - 1);
      assignment.put(neg(literal), 0);
    }
  }

  private void checkContradiction() throws ContradictionException {
    // a -> contradiction
    // -a -> contradiction
    // => contradiction
    for (int i = 0; i < lits.length; i += 2) {
      if (contradiction[i] && contradiction[i + 1]) {
        throw new ContradictionException();
      }
    }
  }

  /** Expands instance to include forced literals. */
  private void addForcedLiterals() {
    int num = 0;

    for (int i = 0; i < forced.size(); i++) {
      int literal = forced.getQuick(i);
      learned.add(encode(1, OR));
      learned.add(literal);
      num++;
    }

    for (int i = 0; i < lits.length; i++) {
      if (forced.contains(lits[i])) {
        continue;
      }

      int p = assignment.get(lits[i]);
      for (int j = 0; j < lits.length; j++) {
        if (i != j && !contradiction[j]) {
          if ((p & (1 << j)) != 0) {
            learned.add(encode(2, OR));
            learned.add(neg(lits[j]));
            learned.add(lits[i]);
            num++;
          }
        }
      }
    }

    instance.formula.addAll(learned);

    if (print > 0 && num > 0) {
      logger.info("Found " + num + " extra clauses at " + this);
      // if (depth == 0) logger.info("Found " + formulaToString(learned));
    }
  }

  /** Updates literal scores based on found contradictions. */
  private void updateScores() {
    for (int i = 0; i < lits.length; i++) {
      if (contradiction[i]) {
        updateScore(scores, depth, var(lits[i]));
      }
    }
  }

  public static int[] dom;

  /** Picks at most NUM_VARIABLES for branching. */
  private void pickVariables() {
    // Scores all variables.
    TIntIntHashMap scores = new TIntIntHashMap();
    ClauseIterator it = new ClauseIterator(instance.formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(instance.formula, clause);
      int delta = 1 + (32 >> length);
      int extra = length == 2 ? 6 : 0;

      for (int i = clause; i < clause + length; i++) {
        int literal = instance.formula.getQuick(i);
        scores.adjustOrPutValue(literal, delta, delta);
        scores.adjustOrPutValue(neg(literal), extra, extra);
      }
    }

    it = new ClauseIterator(instance.formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(instance.formula, clause);

      if (length == 2) {
        final double gamma = 0.3;
        int u = instance.formula.getQuick(clause);
        int v = instance.formula.getQuick(clause + 1);
        scores.put(neg(u), scores.get(neg(u)) + (int) (gamma * scores.get(v)));
        scores.put(neg(v), scores.get(neg(v)) + (int) (gamma * scores.get(u)));
      }
    }

    // Picks the top scores
    int numVariables;
    if (depth == 0) {
      numVariables = NUM_VARIABLES_ROOT;
    } else if (depth < 2) {
      numVariables = NUM_VARIABLES;
    } else {
      numVariables = 1;
    }

    int[] top = new int[numVariables];
    long[] cnt = new long[numVariables];
    int num = 0;

    TIntIntIterator it1 = scores.iterator();
    for (int size = scores.size(); size > 0; size--) {
      it1.advance();
      int l = it1.key();

      if (l == var(l)) {
        // A variable's score depends on scores of both phases.
        int p = scores.get(l);
        int n = scores.get(neg(l));
        long c = 1L * p * n + p + n;

        if (num < top.length) {
          top[num] = l;
          cnt[num] = c;
          num++;
          continue;
        }
        
        if (c > cnt[top.length - 1]) {
          int pos = top.length - 1;
          for (; pos > 0 && c > cnt[pos - 1]; pos--) {
            top[pos] = top[pos - 1];
            cnt[pos] = cnt[pos - 1];
          }

          top[pos] = l;
          cnt[pos] = c;
        }
      }
    }

    num = Math.min(num, numVariables);
    assert num > 0;

    vars = Arrays.copyOf(top, num);
    lits = new int[2 * num];
    contradiction = new boolean[lits.length];
    num = 0;

    for (int i = 0; i < vars.length; i++) {
      assert vars[i] != 0;
      lits[num++] = vars[i];
      lits[num++] = neg(vars[i]);
    }
  }
}
