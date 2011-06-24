package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.iterator.TIntObjectIterator;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

/**
 * Tries to split instance into smaller independent instances.
 *
 * The algorithm is as follows: puts variables in the same clause
 * into the same set using disjoint sets. The instance can be split
 * into variables dictated by the disjoints sets.
 */
public final class SplitActivity extends Activity {
  private static final Logger logger = Logger.getLogger(SplitActivity.class);

  /** Representants in disjoint set.  */
  private int[] repr;
  /** Height of disjoint set. */
  private int[] height;
  /** Array of found units. */
  private TIntArrayList units = null;
  /** Number of subproblems solved. */
  private int numSubmittedSplits = 0;
  /** True if any components is unsatisfiable. */
  private boolean isUnsatisfiable = false;
  /** True if any component is unknown. */
  private boolean isUnknown = false;
  /** Subproblems. */
  private TIntObjectHashMap<Skeleton> subInstances = null;

  public SplitActivity(final ActivityIdentifier parent,
                       final int depth,
                       final long generation,
                       final TDoubleArrayList scores,
                       final Skeleton instance) {
    super(parent, depth, generation, scores, instance);
  }

  @Override
  public void initialize() {
    if (!Configure.split) {
      executor.submit(new SelectBranchActivity(
            parent, depth, generation, scores, instance));
      finish();
      return;
    }

    int numVariables = instance.numVariables;
    repr = new int[numVariables + 1];
    height = new int[numVariables + 1];
    units = new TIntArrayList(numVariables);

    joinVariablesInClauses(instance.formula);

    if (!isSplit()) {
      executor.submit(new SelectBranchActivity(
            parent, depth, generation, scores, instance));
      finish();
      return;
    }

    split();

    // Submits subInstance to solve
    numSubmittedSplits = subInstances.size();
    TIntObjectIterator<Skeleton> it = subInstances.iterator();
    for (int size = subInstances.size(); size > 0; size--) {
      it.advance();
      executor.submit(new SelectBranchActivity(
            identifier(), depth, generation, scores, it.value()));
    }

    suspend();
  }

  @Override
  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    numSubmittedSplits--;

    if (response.isUnsatisfiable()) {
      // For unsatisfiable case sends solution as fast as possible.
      executor.send(new Event(identifier(), parent, Solution.unsatisfiable()));
      isUnsatisfiable = true;
    }

    if (response.isUnknown()) {
      isUnknown = true;
    }

    if (!isUnsatisfiable && !isUnknown) {
      mergeNewUnits(response.units());
    }

    if (numSubmittedSplits > 0) {
      suspend();
    } else {
      if (isUnknown) {
        reply(Solution.unknown());
      } else if (!isUnsatisfiable) {
        reply(Solution.satisfiable(units));
      }
      finish();
    }
  }

  /** Puts variables in each clause in the same set. */
  private void joinVariablesInClauses(final TIntArrayList formula) {
    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);

      int u = formula.get(clause);
      for (int i = clause; i < clause + length; i++) {
        join(u, formula.getQuick(i));
      }
    }
  }

  /** Returns true if this instance can be split. */
  private boolean isSplit() {
    for (int u = 0, v = 1; v < instance.numVariables; v++) {
      if (repr[v] != 0) {
        if (u == 0) {
          u = find(v);
        } else if (u != find(v)) {
          return true;
        }
      }
    }

    return false;
  }

  /** Splits the instance in sub instances. */
  private void split() {
    subInstances = new TIntObjectHashMap<Skeleton>();

    final TIntArrayList formula = instance.formula;
    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int type = type(formula, clause);

      int u = formula.get(clause);
      Skeleton subInstance = subInstances.get(find(u));
      if (subInstance == null) {
        subInstance = new Skeleton(instance.numVariables);
        subInstances.put(find(u), subInstance);
      }

      TIntArrayList subFormula = subInstance.formula;
      subFormula.add(encode(length, type));
      for (int i = clause; i < clause + length; i++) {
        subFormula.add(formula.get(i));
      }
    }
  }

  /** Adds new units from a sub instance. */
  private void mergeNewUnits(final TIntArrayList newUnits) {
    units.addAll(newUnits);
  }

  /** Returns the top representant of u. */
  private int find(int u) {
    u = Math.abs(u);
    if (repr[u] == 0) {
      repr[u] = u;
      return u;
    }
    return findInternal(u);
  }

  /** Helper function for find. */
  private int findInternal(final int u) {
    if (repr[u] == u) {
      return u;
    }
    repr[u] = find(repr[u]);
    return repr[u];
  }

  /** Joins u and v's sets. */
  private void join(int u, int v) {
    u = find(u);
    v = find(v);
    if (height[u] < height[v]) {
      repr[u] = v;
    } else if (height[u] > height[v]) {
      repr[v] = u;
    } else if (u != v) {
      repr[u] = v;
      height[v]++;
    }
  }
}
