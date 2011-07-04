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
public final class PropagateActivity extends Activity {
  /** Selected variables for branching. */
  private int[] vars = null;
  /** Literals in each propagation. */
  private int[] lits = null;  
  /** True if propagation is contradiction. */
  private boolean[] contradiction = null;
  /** List of forced literals. */
  private TIntHashSet forced = new TIntHashSet();
  /** Assignments for each literals (2 * num selected variables). */
  private TIntIntHashMap assignment = null;
  /** Total number of clauses. */
  private int numClauses = 0;
  /** Number of unsatisfied clauses for each propagation. */
  private int[] numUnsatisfied = null;

  public PropagateActivity(final ActivityIdentifier parent,
                           final Skeleton instance,
                           final int[] vars) {
    super(parent, 0, 0, null, instance);
    this.vars = vars;
  }

  @Override
  public void initialize() {
    initialAssignment();

    try {
    for (int repeat = 10; repeat > 0; repeat--) {
        int tmp = forced.size();
        propagate();
        findExtraContradictions();
        findExtraForced();
        checkContradiction();
        repeat += forced.size() > tmp + 1 ? 1 : 0;
      }

      findExtraContradictions();
      checkContradiction();
    } catch (ContradictionException e) {
      reply(Solution.unsatisfiable());
      finish();
      return;
    }


    Solution solution = Solution.unknown();
    solution.learnClauses(getClauses());
    reply(solution);
    finish();
    return;
  }

  /** Sets initial literal assignment. */
  private void initialAssignment() {
    lits = new int[2 * vars.length];
    contradiction = new boolean[lits.length];

    for (int i = 0; i < vars.length; i++) {
      assert vars[i] != 0;
      lits[2 * i + 0] = vars[i];
      lits[2 * i + 1] = neg(vars[i]);
    }

    assignment = new TIntIntHashMap();
    for (int i = 0; i < lits.length; i++) {
      assign(lits[i], i);
    }
  }

  /** Assigns literal in propagation index. */
  private void assign(final int literal, final int index) {
    assignment.put(literal, assignment.get(literal) | (1 << index));
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

  /** Checks if the instance is proven to be a contradiction. */
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

  /** Finds forced literals. */
  private void findExtraForced() {
    findExtraContradictions();

    // In a contradiction any assignment can be assumed
    int any = 0;
    for (int i = 0; i < lits.length; i++) {
      if (contradiction[i]) {
        any |= 1 << i;
      }
    }

    // a -> c
    // -a -> c
    // => c
    final int mask5 = 0x55555555;
    TIntIntIterator it = assignment.iterator();
    for (int size = assignment.size(); size > 0; size--) {
      it.advance();
      int l = it.key();
      int p = it.value() | any;
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

    for (int literal : forced.toArray()) {
      assignment.put(literal, (1 << lits.length) - 1);
      assignment.put(neg(literal), 0);
    }
  }

  /** Expands instance to include forced literals. */
  private TIntArrayList getClauses() {
    TIntIntIterator it;
    TIntArrayList learned = new TIntArrayList();
    int num = 0;

    // Adds obligatory forced literals.
    for (int literal : forced.toArray()) {
      learned.add(encode(1, OR));
      learned.add(literal);
      num++;
    }

    // Searches binaries among selected variables.
    for (int i = 0; i < lits.length; i++) {
      if (forced.contains(lits[i]) || forced.contains(neg(lits[i]))) {
        continue;
      }

      for (int j = 0; j < lits.length; j++) {
        if (forced.contains(lits[j]) || forced.contains(neg(lits[j]))) {
          continue;
        }

        if (var(lits[i]) < var(lits[j])
            && (assignment.get(lits[j]) & (1 << i)) != 0) {
          learned.add(encode(2, OR));
          learned.add(neg(lits[i]));
          learned.add(lits[j]);
          num++;
        }
      }
    }

    // a -> c
    // b -> -c
    // => a -> -b
    it = assignment.iterator();
    for (int size = assignment.size(); size > 0; size--) {
      it.advance();
      int l = it.key();
      int p = it.value();
      int n = assignment.get(neg(l));

      for (int i = 0; i < lits.length; i++) {
        for (int j = 0; j < lits.length; j++) {
          if ((p & (1 << i)) != 0 && ((n & (1 << j)) != 0)) {
            if (var(lits[i]) < var(lits[j])
                && !contradiction[i] && !contradiction[j]) {
              learned.add(encode(2, OR));
              learned.add(neg(lits[i]));
              learned.add(neg(lits[j]));
              num++;
            }
          }
        }
      }
    }

    // Searches equivalent literals
    final int mask5 = 0x55555555;
    it = assignment.iterator();
    for (int size = assignment.size(); size > 0; size--) {
      it.advance();
      int l = it.key();
      int p = it.value();
      int n = assignment.get(neg(l));

      if (forced.contains(l) || forced.contains(neg(l))) {
        continue;
      }

      if ((p & (n >> 1) & mask5) != 0) {
        for (int j = 0; j < lits.length; j++) {
          if (forced.contains(lits[j]) || forced.contains(neg(lits[j]))) {
            continue;
          }

          if (var(l) < var(lits[j]) && (p & (n >> 1) & (1 << j)) != 0) {
            learned.add(encode(2, OR));
            learned.add(neg(lits[j]));
            learned.add(l);
            num++;

            learned.add(encode(2, OR));
            learned.add(lits[j]);
            learned.add(neg(l));
            num++;

            logger.info("eq " + l + " " + lits[j]);
          }
        }
      }
    }

    logger.info("Found " + num + " extra clauses");
    return learned;
  }
}
