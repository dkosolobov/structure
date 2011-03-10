package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;

import static ibis.structure.Misc.*;


public final class ImplicationsGraph {
  // An optimizations for nodes with no edges.
  private static final TIntArrayList EMPTY = new TIntArrayList();

  private final int numVariables;
  private final TIntArrayList[] edges;
  private final TouchSet visited;
  private final int[] topologicalSort;
  private final int[] colapsed;
  private final int[] stack;

  public ImplicationsGraph(final int numVariables) {
    this.numVariables = numVariables;

    edges = new TIntArrayList[2 * numVariables + 1];
    for (int u = 0; u < 2 * numVariables + 1; u++) {
      edges[u] = EMPTY; // new TIntArrayList();
    }

    topologicalSort = create();
    visited = new TouchSet(numVariables);
    colapsed = create();
    stack = create();
  }

  /** Adds a new implication u &rarr; v. */
  public void add(final int u, final int v) {
    createLiteral(u);
    createLiteral(neg(v));

    edges(u).add(v);
    edges(neg(v)).add(neg(u));
  }

  /** Creats edges for literals u and -u */
  private void createLiteral(final int u) {
    if (edges(u) == EMPTY) {
      edges[u + numVariables] = new TIntArrayList();
    }
  }

  /** Returns true if implication u &rarrr; v is valid */
  public boolean contains(final int u, final int v) {
    return edges(u).contains(v);
  }

  /** Returns the edges list for node u. */
  public TIntArrayList edges(final int u) {
    return edges[u + numVariables];
  }

  /**
   * Propagates assignment of all literals and returns propagations
   *
   * @param literals units to propagate
   * @return list of units propagated
   * @throws ContradictionException if a literal and its negation are propagated.
   */
  public TIntArrayList propagate(final TIntArrayList literals)
      throws ContradictionException {
    visited.reset();

    TIntArrayList visited = new TIntArrayList();
    for (int i = 0; i < literals.size(); i++) {
      int u = literals.get(i);
      internalBFS(u, visited);
    }

    for (int i = 0; i < visited.size(); i++) {
      int v = visited.get(i);
      if (visited.contains(-v)) {
        throw new ContradictionException();
      }
    }

    remove(visited);
    return visited;
  }

  public TIntArrayList propagate(final int... literals) throws ContradictionException {
    return propagate(new TIntArrayList(literals));
  }

  /** Removes all edges of literal and -literal. */
  public void remove(final int literal) {
    TIntArrayList literals = new TIntArrayList();
    literals.add(literal);
    remove(literals);
  }

  /**
   * Removes a list of literals and their negation.
   *
   * u and -u cannot be both in literals.
   */
  private void remove(final TIntArrayList literals) {
    // Finds all nodes that have dirty edges.
    visited.reset();
    int stackTop = 0;
    for (int i = 0; i < literals.size(); i++) {
      int u = literals.get(i);

      for (int j = 0; j < edges(-u).size(); j++) {
        int v = -edges(-u).get(j);
        if (!visited.containsOrAdd(v)) {
          stack[stackTop] = v;
          stackTop++;
        }
      }

      for (int j = 0; j < edges(u).size(); j++) {
        int v = -edges(u).get(j);
        if (!visited.containsOrAdd(v)) {
          stack[stackTop] = v;
          stackTop++;
        }
      }
    }

    // Marks all literals to be removed.
    visited.reset();
    for (int i = 0; i < literals.size(); i++) {
      int u = literals.get(i);

      assert !visited.contains(u): "Literal " + u + " was already removed";
      visited.add(u);
      visited.add(neg(u));

      assert EMPTY.isEmpty();
      edges[u + numVariables] = EMPTY;
      edges[neg(u) + numVariables] = EMPTY;
    }

    // Removes unwanted literals from dirty nodes.
    for (int i = 0; i < stackTop; i++) {
      int u = stack[i];

      int p = 0;
      for (int j = 0; j < edges(u).size(); j++) {
        int v = edges(u).get(j);
        if (!visited.contains(v)) {
          edges(u).set(p, v);
          p++;
        }
      }

      edges(u).remove(p, edges(u).size() - p);
    }
  }

  /**
   * Solves the 2-SAT encoded in the implication graph.
   *
   * Requires all strongly connected components removed.
   */
  public int[] solve(final TIntArrayList assigned)
      throws ContradictionException {
    topologicalSort();

    visited.reset();
    for (int i = 0; i < assigned.size(); i++) {
      int u = assigned.get(i);
      assert edges(u).isEmpty() && edges(neg(u)).isEmpty();
      visited.add(u);
      visited.add(neg(u));
    }

    TIntHashSet units = new TIntHashSet();
    for (int i = topologicalSort.length - 1; i >= 0; i--) {
      int u = topologicalSort[i];
      if (u == 0 || visited.contains(u)) {
        continue;
      }

      for (int j = 0; j < edges(neg(u)).size(); j++) {
        // v is the parent of u
        int v = -edges(neg(u)).get(j);
        if (units.contains(v)) {
          units.add(u);
          break;
        }
      }

      // u was not forced by any parent so it should be false
      if (!units.contains(u)) {
        units.add(neg(u));
      }

      visited.add(u);
      visited.add(neg(u));
    }

    return units.toArray();
  }

  /**
   * Finds all strongly connected components.
   *
   * The implemnted algorithm is described at
   *   http://www.cs.berkeley.edu/~vazirani/s99cs170/notes/lec12.pdf
   */
  private void findStronglyConnectedComponents() {
    topologicalSort();
    for (int u = -numVariables; u <= numVariables; u++) {
      set(colapsed, u, u);
    }

    visited.reset();
    TIntArrayList component = new TIntArrayList();
    for (int i = 0; i < topologicalSort.length; i++) {
      component.reset();
      internalBFS(topologicalSort[i], component);
      if (component.size() <= 1) {
        continue;
      }

      // Picks the smallest variable as the component name
      // This will pick the same variable on the component with negated literals.
      int best = component.getQuick(0);
      for (int j = 0; j < component.size(); j++) {
        int u = component.getQuick(j);
        if (var(best) > var(u)) {
          best = u;
        }
      }

      for (int j = 0; j < component.size(); j++) {
        int u = component.getQuick(j);
        set(colapsed, u, best);
      }
    }
  }

  /**
   * Removes all strongly connected components.
   */
  public int[] removeStronglyConnectedComponents()
      throws ContradictionException {
    findStronglyConnectedComponents();

    // Checks if a literal and its negation are in
    // the same cycle which means there is a contradiction.
    for (int u = 1; u <= numVariables; u++) {
      if (get(colapsed, u) == get(colapsed, -u)) {
        throw new ContradictionException();
      }
    }

    // Renames literals and remove duplicates.
    for (int u = -numVariables; u <= numVariables; u++) {
      if (get(colapsed, u) != u) {
        // Moves u's edges into parent
        edges(get(colapsed, u)).addAll(edges(u));
        edges[u + numVariables] = EMPTY;
        continue;
      }
    }

    for (int u = -numVariables; u <= numVariables; u++) {
      if (get(colapsed, u) != u) {
        continue;
      }

      visited.reset();
      visited.add(u);
      int pos = 0;
      for (int j = 0; j < edges(u).size(); j++) {
        int v = get(colapsed, edges(u).getQuick(j));
        if (!visited.containsOrAdd(v)) {
          edges(u).set(pos++, v);
        }
      }
      int removed = edges(u).size() - pos;
      if (removed != 0) {  // bug in trove4j
        edges(u).remove(pos, removed);
      }
    }

    // System.err.println("Removed " + removed + " edges");
    return java.util.Arrays.copyOfRange(colapsed, numVariables, 2 * numVariables + 1);
  }

  /** Performs a breadth first search keeping track of visited nodes. */
  private void internalBFS(final int u, final TIntArrayList seen) {
    if (visited.containsOrAdd(u)) {
      return;
    }

    int stackTail = 0, stackHead = 0;
    stack[stackHead++] = u;

    while (stackTail < stackHead) {
      final int w = stack[stackTail++];
      final TIntArrayList edges = edges(w);
      for (int i = 0; i < edges.size(); i++) {
        int v = edges.getQuick(i);
        if (!visited.containsOrAdd(v)) {
          stack[stackHead++] = v;
        }
      }
    }

    seen.add(stack, 0, stackHead);
  }

  /**
   * Performs a depth first search keeping track of visited nodes.
   *
   * TODO: Function was changed to do BFS so it should be renamed.
   */
  public void dfs(final int start, final TIntArrayList seen) {
    if (seen.isEmpty()) {
      visited.reset();
    }

    internalBFS(start, seen);
  }

  /** Performs a depth first search keeping track of visited nodes. */
  public void dfs(final int start, final TouchSet seen) {
    if (seen.containsOrAdd(start)) {
      return;
    }

    int stackTop = 0;
    stack[stackTop++] = start;

    while (stackTop > 0) {
      int u = stack[--stackTop];
      for (int i = 0; i < edges(u).size(); i++) {
        int v = edges(u).getQuick(i);
        if (!seen.containsOrAdd(v)) {
          stack[stackTop++] = v;
        }
      }
    }
  }

  /** Does a topological sort and stores it in topologicalSort array. */
  public void topologicalSort() {
    visited.reset();
    for (int time = 0, u = -numVariables; u <= numVariables; u++) {
      time = topologicalSort(u, time);
    }

    for (int i = 0; i < topologicalSort.length / 2; i++) {
      int temp = topologicalSort[i];
      topologicalSort[i] = topologicalSort[topologicalSort.length - i - 1];
      topologicalSort[topologicalSort.length - i - 1] = temp;
    }
  }

  /** Orders nodes by exit times in reversed graph. */
  private int topologicalSort(final int u, int time) {
    if (visited.containsOrAdd(u)) {
      return time;
    }

    for (int i = 0; i < edges(-u).size(); i++) {
      int v = -edges(-u).get(i);
      time = topologicalSort(v, time);
    }

    topologicalSort[time] = u;
    time++;
    return time;
  }

  /** Verifies the implication graph for consistency */
  public void verify() {
    if (!Configure.enableExpensiveChecks) {
      return;
    }

    for (int u = -numVariables; u <= numVariables; u++) {
      set(stack, u, 0);
    }

    for (int u = -numVariables; u <= numVariables; u++) {
      for (int i = 0; i < edges(u).size(); i++) {
        int v = edges(u).get(i);
        set(stack, v, get(stack, v) + 1);
        assert edges(-v).contains(-u)
            : "Missing reverse edge " + neg(v) + " -> " + neg(u);
      }
    }

    for (int u = -numVariables; u <= numVariables; u++) {
      assert get(stack, u) == edges(neg(u)).size()
          : "Wrong number of edges for literal " + neg(u);
    }
  }

  /** Returns the implication graph as a dot digraph. */
  public String toString() {
    StringBuffer buffer = new StringBuffer();
    buffer.append("digraph ig {\n");
    for (int u = -numVariables; u <= numVariables; u++) {
      if (!edges(u).isEmpty()) {
        buffer.append(u + " -> " + edges(u).toString().replace(',', ';') + ";\n");
      }
    }
    buffer.append("}");
    return buffer.toString();
  }

  private int[] create() {
    return new int[2 * numVariables + 1];
  }

  private int get(final int[] array, final int u) {
    return array[u + numVariables];
  }

  private void set(final int[] array, final int u, final int v) {
    array[u + numVariables] = v;
  }
}
