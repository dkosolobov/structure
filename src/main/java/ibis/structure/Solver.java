package ibis.structure;

import java.text.DecimalFormat;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TLongArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntIterator;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import static ibis.structure.BitSet.mapZtoN;
import static ibis.structure.BitSet.mapNtoZ;;


/**
 * Solver.
 *
 * Represents the core SAT solver.
 *
 * TODO:
 * 1) Binary self summing
 * 2) Blocked clause elimination
 */
public final class Solver {
  private static final Logger logger = Logger.getLogger(Solver.class);
  private static final int REMOVED = Integer.MAX_VALUE;
  private static final int BACKTRACK_THRESHOLD = 0; // 1 << 8;
  private static final int BACKTRACK_MAX_CALLS = 1 << 12;
  private static final java.util.Random random = new java.util.Random(1);

  // Number of variables
  private int numVariables;
  // Set of true literals discovered.
  private BitSet units = new BitSet();
  // Equalities between literals.
  private int[] proxies;
  // The implication graph.
  private DAG dag;
  // List of clauses separated by 0.
  private TIntArrayList clauses;
  // Watchlists
  private TIntHashSet[] watchLists;
  // Maps start of clause to # unsatisfied literals
  private TIntIntHashMap lengths = new TIntIntHashMap();
  // Queue of clauses with at most 2 unsatisfied literals.
  // May contains satisfied clauses.
  private TIntArrayList queue = new TIntArrayList();

  public Solver(final Skeleton instance, final int branch) {
    numVariables = instance.numVariables;
    proxies = new int[numVariables + 1];
    dag = new DAG(numVariables);
    clauses = (TIntArrayList) instance.clauses.clone();
    watchLists = new TIntHashSet[2 * numVariables + 1];

    for (int literal = 1; literal <= numVariables; ++literal) {
      watchLists[mapZtoN(literal)] = new TIntHashSet();
      watchLists[mapZtoN(-literal)] = new TIntHashSet();
      proxies[literal] = literal;
    }

    buildWatchLists();
    if (branch != 0) {
      addUnit(branch);
    }

    // logger.info("Solving " + clauses.size() + " literals and "
                // + numVariables + " variables, branching on " + branch);
    System.err.print(".");
    System.err.flush();
  }

  /**
   * Builds watch lists.
   */
  private void buildWatchLists() {
    int start = 0;
    boolean satisfied = false;

    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.get(i);
      if (literal == 0) {
        int length = i - start;
        lengths.put(start, length);
        if (length <= 2) queue.add(start);
        if (satisfied) removeClause(start);
        start = i + 1;
        satisfied = false;
      } else {
        if (watchList(literal).contains(start)) {  // duplicate literal
          logger.error("Found duplicate literal");
          clauses.set(i, REMOVED);
        } else {
          watchList(literal).add(start);
          if (watchList(-literal).contains(start)) {
            logger.error("Found satisfied clause");
            satisfied = true;
          }
        }
      }
    }
  }

  public TIntArrayList cleanClause() {
    TIntArrayList clean = new TIntArrayList();
    for (int i = 0; i < clauses.size(); i++) {
      int literal = clauses.get(i);
      if (literal != REMOVED) {
        clean.add(literal);
      }
    }
    return clean;
  }


  /**
   * Returns current (simplified) instance.
   *
   * Includes units and equivalent literals.
   *
   * @return a skeleton
   */
  public Skeleton skeleton() {
    Skeleton skeleton = new Skeleton();

    // Appends the implications graph and clauses
    skeleton.append(dag.skeleton());
    skeleton.append(cleanClause());

    // Appends units and equivalent relations
    for (int literal = -numVariables; literal <= numVariables; ++literal) {
      if (literal != 0) {
        int proxy = getRecursiveProxy(literal);
        if (units.contains(proxy)) {
          skeleton.add(literal);
        } else if (literal != proxy && !units.contains(-proxy)) {
          // literal and proxy are equivalent,
          // but proxy is not assigned
          skeleton.add(literal, -proxy);
        }
      }
    }

    return skeleton;
  }

  /**
   * Returns the current DAG.
   */
  public DAG dag() {
    return dag;
  }

  /**
   * Returns string representation of stored instance.
   */
  public String toString() {
    Skeleton skeleton = skeleton();
    skeleton.canonicalize();
    return skeleton.toString();
  }

  /**
   * Returns a normalized literal to branch on.
   */
  public Solution solve() {
    int beforeNumLiterals = clauses.size();

    int solved = Solution.UNKNOWN;
    try {
      simplify();
      if (clauses.size() == 0) {
        solved = Solution.SATISFIABLE;
      }
    } catch (ContradictionException e) {
      return Solution.unsatisfiable();
    }

    // Computes proxy for all literal.
    for (int literal = 1; literal <= numVariables; ++literal) {
      getRecursiveProxy(literal);
    }
    if (solved == Solution.SATISFIABLE) {
      logger.info("solution = " + (new TIntArrayList(units.elements())));
      return Solution.satisfiable(units.elements(), proxies);
    }

    // Gets the core instance that needs to be solved further
    Skeleton core = new Skeleton();
    core.append(dag.skeleton());
    core.append(cleanClause());

    // The idea here is that if a literal is frequent
    // it will have a higher chance to be selected
    int bestBranch = 0;
    int bestValue = Integer.MIN_VALUE;
    for (int i = 0; i < core.clauses.size(); ++i) {
      int literal = core.clauses.get(i);
      if (literal != 0) {
        int value = random.nextInt();
        if (value > bestValue) {
          bestValue = value;
          bestBranch = literal;
        }
      }
    }

    int afterNumLiterals = core.clauses.size();
    // logger.info("Reduced from " + beforeNumLiterals + " to " + afterNumLiterals
                // + " (diff " + (beforeNumLiterals - afterNumLiterals) + ")");
    return Solution.unknown(units.elements(), proxies, core, bestBranch);
  }

  /**
   * Simplifies the instance.
   */
  public void simplify() throws ContradictionException {
    propagate();
  }

  private boolean propagate() throws ContradictionException {
    for (int i = 0; i < queue.size(); ++i) {
      int start = queue.get(i);
      if (!lengths.containsKey(start)) {
        // clause already satified.
        continue;
      }
      int length = lengths.get(start);
      switch (length) {
        case 0:
          throw new ContradictionException();

        case 1:
          int u;
          while ((u = clauses.get(start)) == REMOVED) {
            start++;
          }
          addUnit(u);
          break;

        default:
      }
    }

    boolean simplified = !queue.isEmpty();
    queue.clear();
    return simplified;
  }

  /**
   * Returns the watch list for literal u.
   */
  private TIntHashSet watchList(int u) {
    return watchLists[mapZtoN(u)];
  }

  /**
   * Returns true if u is already assigned.
   */
  private boolean isAssigned(final int u) {
    assert u != 0;
    return getRecursiveProxy(u) != u || units.get(u) || units.get(-u);
  }

  /**
   * Removes literal u from clause.
   *
   * @param clause start of clause
   * @param u literal to remove
   */
  private void removeLiteral(final int clause, final int u) {
    assert u != 0;
    for (int c = clause; ; c++) {
      if (u == clauses.get(c)) {
        watchList(u).remove(clause);
        clauses.set(c, REMOVED);
        int newLength = lengths.adjustOrPutValue(clause, -1, 0xA3A3A3A3);
        if (newLength <= 2) queue.add(clause);
        break;
      }
    }
  }

  /**
   * Removes one clause updating the watchlists.
   *
   * @param clause start of clause
   */
  private void removeClause(final int clause) {
    lengths.remove(clause);
    for (int c = clause; ; c++) {
      int literal = clauses.get(c);
      clauses.set(c, REMOVED);
      if (literal == 0) break;
      if (literal == REMOVED) continue;
      watchList(literal).remove(clause);
    }
  }

  /**
   * Propagates one unit assignment.
   *
   * @param u unit to propagate
   */
  private void propagateUnit(final int u) {
    // Removes clauses satisfied by u.
    for (int clause : watchList(u).toArray()) {
      removeClause(clause);
    }
    assert watchList(u).isEmpty();

    // Removes -u.
    for (int clause : watchList(-u).toArray()) {
      removeLiteral(clause, -u);
    }
    assert watchList(-u).isEmpty();
  }

  /**
   * Adds a new unit.
   *
   * @param u unit to add.
   */
  private void addUnit(final int u) {
    assert !isAssigned(u);

    if (dag.hasNode(u)) {
      int[] neighbours = dag.neighbours(u).toArray();
      for (int unit : neighbours) {
        propagateUnit(u);
        units.add(u);
      }
      dag.delete(neighbours);
    } else {
      propagateUnit(u);
      units.add(u);
    }
  }

  /**
   * Recursively finds the proxy of u.
   * The returned value doesn't have any proxy.
   */
  private int getRecursiveProxy(final int u) {
    assert u != 0;
    if (u < 0) {
      return -getRecursiveProxy(proxies[-u]);
    }
    if (u != proxies[u]) {
      proxies[u] = getRecursiveProxy(proxies[u]);
      return proxies[u];
    }
    return u;
  }
}
