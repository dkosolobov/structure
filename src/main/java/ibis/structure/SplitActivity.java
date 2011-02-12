package ibis.structure;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import gnu.trove.TObjectIntHashMap;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;


public final class SplitActivity extends Activity {
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
                       final Skeleton instance) {
    super(parent, depth, instance);
  }

  public void initialize() {
    if (!Configure.split || instance.clauses.size() < 256) {
      executor.submit(new BranchActivity(parent, depth, instance));
      finish();
      return;
    }

    int numVariables = instance.numVariables;
    repr = new int[numVariables + 1];
    height = new int[numVariables + 1];
    units = new int[numVariables];

    // Literals in the same clause belong to the same set.
    TIntArrayList clauses = instance.clauses;
    for (int start = 0; start < clauses.size(); start++) {
      int u = clauses.get(start);
      for (int end = start; ; end++) {
        int v = clauses.get(end);
        if (v == 0) {
          start = end;
          break;
        }
        join(u, v);
      }
    }

    // Checks if there are at least two components.
    boolean isSplit = false;
    int aSet = find(clauses.get(0));
    for (int u = 1; u <= numVariables; ++u) {
      if (repr[u] != 0 && find(u) != aSet) {
        isSplit = true;
        break;
      }
    }
    if (!isSplit) {
      executor.submit(new BranchActivity(parent, depth, instance));
      finish();
      return;
    }

    // Splits instance into at least two sub-problems.
    subInstances = new TIntObjectHashMap<Skeleton>();
    for (int start = 0; start < clauses.size(); start++) {
      int u = clauses.get(start);
      int uSet = find(u);

      Skeleton subInstance = subInstances.get(uSet);
      if (subInstance == null) {
        subInstance = new Skeleton();
        subInstances.put(uSet, subInstance);
      }

      for (int end = start; ; end++) {
        int v = clauses.get(end);
        assert v == 0 || uSet == find(v);
        subInstance.clauses.add(v);
        if (v == 0) {
          start = end;
          break;
        }
      }
    }

    TIntObjectIterator<Skeleton> it;
    final int backtrackThreshold = 64;
    
    int numSolvedSplits = 0;
    it = subInstances.iterator();
    for (int size = subInstances.size(); size > 0; size--) {
      it.advance();
      clauses = it.value().clauses;
      if (clauses.size() <= backtrackThreshold) {
        int[] newUnits = backtrack(clauses);
        if (newUnits == null) {
          logger.info("backtrack found unsatisfiable subInstance");
          reply(Solution.unsatisfiable());
          finish();
          return;
        }
        mergeUnits(newUnits);
        numSolvedSplits++;
      }
    }

    TIntArrayList sizes = new TIntArrayList();
    it = subInstances.iterator();
    for (int size = subInstances.size(); size > 0; size--) {
      it.advance();
      clauses = it.value().clauses;
      if (clauses.size() > backtrackThreshold) {
        numSubmittedSplits++;
        sizes.add(clauses.size());
        executor.submit(new BranchActivity(identifier(), depth, it.value()));
      }
    }

    logger.info("Split " + instance.clauses.size() + " into " + sizes
        + " and " + numSolvedSplits + " smaller");
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
      mergeUnits(response.units());
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
        assert numUnits == units.length: " " + instance.clauses.size();
        Solution solution = Solution.satisfiable(units);
        verify(solution);
        reply(solution);
      }
      finish();
    }
  }

  private void mergeUnits(int[] newUnits) {
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

  /** Finds a solution for a formula using backtracking */
  private static int[] backtrack(TIntArrayList clauses) {
    TIntHashSet assigned = new TIntHashSet();
    if (!backtrack(clauses, 0, assigned)) {
      return null;
    }

    // Assigned missing literals
    for (int i = 0; i < clauses.size(); i++) {
      int u = clauses.get(i);
      if (u != 0 && !assigned.contains(-u) && !assigned.contains(u)) {
        assigned.add(u);
      }
    }

    return assigned.toArray();
  }

  private static boolean backtrack(TIntArrayList clauses, int start, TIntHashSet assigned) {
    if (start == clauses.size()) {
      return true;
    }

    int end = start;
    for (;; end++) {
      if (clauses.get(end) == 0) {
        break;
      }
    }

    boolean satisfied = false;
    for (int i = start; i < end; i++) {
      int u = clauses.get(i);
      if (assigned.contains(u)) {
        satisfied = true;
        break;
      }
    }

    // Clause already satisfied, move on.
    if (satisfied) {
      return backtrack(clauses, end + 1, assigned);
    }

    // Clause not satisfied, tries to assigned each literal.
    for (int i = start; i < end; i++) {
      int u = clauses.get(i);
      if (assigned.contains(-u)) {
        continue;
      }

      assigned.add(u);
      if (backtrack(clauses, end + 1, assigned)) {
        return true;
      }
      assigned.remove(u);
    }

    return false;
  }
}
