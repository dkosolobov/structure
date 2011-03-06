package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.iterator.TIntObjectIterator;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class SplitActivity extends Activity {
  private static final int BACKTRACK_THRESHOLD = 64;
  private static final Logger logger = Logger.getLogger(SplitActivity.class);

  /** Representants in disjoint set  */
  private int[] repr;
  /** Height of disjoint set */
  private int[] height;
  /** Array of found units */
  private int[] units = null;
  /** Number of found units */
  private int numUnits = 0;
  /** Number of subproblems solved */
  private int numSubmittedSplits = 0;
  /** True if any components is unsatisfiable */
  private boolean isUnsatisfiable = false;
  /** True if any component is unknown */
  private boolean isUnknown = false;
  /** Subproblems */
  private TIntObjectHashMap<Skeleton> subInstances = null;

  public SplitActivity(final ActivityIdentifier parent,
                       final int depth,
                       final Skeleton instance,
                       final boolean learn) {
    super(parent, depth, instance, learn);
  }

  public void initialize() {
    if (!Configure.split) {
      executor.submit(new SelectBranchActivity(
            parent, depth, instance, learn));
      finish();
      return;
    }

    int numVariables = instance.numVariables;
    repr = new int[numVariables + 1];
    height = new int[numVariables + 1];
    units = new int[numVariables];

    joinVariablesInClauses(instance.formula);
    joinVariablesInBinaries(instance.bins);

    if (!isSplit()) {
      executor.submit(new SelectBranchActivity(
            parent, depth, instance, learn));
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
            identifier(), depth, it.value(), learn));
    }

    gc();
    suspend();
  }

  public void process(Event e) throws Exception {
    Solution response = (Solution)e.data;
    numSubmittedSplits--;

    if (response.isUnsatisfiable()) {
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
      if (isUnsatisfiable) {
        // Solution already sent.
      } else if (isUnknown) {
        reply(Solution.unknown());
      } else {
        units = java.util.Arrays.copyOf(units, numUnits);
        Solution solution = Solution.satisfiable(units);
        verify(solution);
        reply(solution);
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

  /** Puts variables in each binary in the same set. */
  private void joinVariablesInBinaries(final TIntArrayList bins) {
    for (int i = 0; i < bins.size(); i += 2) {
      join(bins.getQuick(i), bins.getQuick(i + 1));
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

    final TIntArrayList bins = instance.bins;
    for (int i = 0; i < bins.size(); i += 2) {
      int u = bins.getQuick(i);
      int v = bins.getQuick(i + 1);

      Skeleton subInstance = subInstances.get(find(u));
      if (subInstance == null) {
        subInstance = new Skeleton(instance.numVariables);
        subInstances.put(find(u), subInstance);
      }

      subInstance.bins.add(u);
      subInstance.bins.add(v);
    }
  }

  /** Adds new units from a sub instance */
  private void mergeNewUnits(int[] newUnits) {
    System.arraycopy(newUnits, 0, units, numUnits, newUnits.length);
    numUnits += newUnits.length;
  }

  /** Returns the top representant of u */
  private int find(int u) {
    u = Math.abs(u);
    if (repr[u] == 0) {
      repr[u] = u;
      return u;
    }
    return findInternal(u);
  }

  private int findInternal(int u) {
    if (repr[u] == u) {
      return u;
    }
    repr[u] = find(repr[u]);
    return repr[u];
  }

  /** Joins u and v's sets */
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
