package ibis.structure;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;

import static ibis.structure.Misc.*;


public class ImplicationsGraph {
  private static final TIntArrayList EMPTY = new TIntArrayList();

  private int numVariables;
  private TIntArrayList[] edges = null;
  private TouchSet visited;
  private int[] topologicalSort;
  private int[] colapsed;
  private int[] stack;

  public ImplicationsGraph(int numVariables) {
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
  public void add(int u, int v) {
    createLiteral(u);
    createLiteral(-v);

    edges(u).add(v);
    edges(-v).add(-u);
  }

  /** Creats edges for literals u and -u */
  private void createLiteral(int u) {
    if (edges(u) == EMPTY) {
      edges[u + numVariables] = new TIntArrayList();
      // edges[-u + numVariables] = new TIntArrayList();
    }
  }

  /** Returns true if implication u &rarrr; v is valid */
  public boolean contains(int u, int v) {
    return edges(u).contains(v);
  }

  /** Returns the edges list for node u. */
  public TIntArrayList edges(int u) {
    return edges[u + numVariables];
  }

  /**
   * Propagates assignment of all literals and returns propagations
   *
   * @param literals units to propagate
   * @return list of units propagated
   * @throws ContradictionException if a literal and its negation are propagated.
   */
  public TIntArrayList propagate(TIntArrayList literals) throws ContradictionException {
    visited.reset();

    TIntArrayList visited = new TIntArrayList();
    for (int i = 0; i < literals.size(); i++) {
      int u = literals.get(i);
      internalDFS(u, visited);
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

  public TIntArrayList propagate(int... literals) throws ContradictionException {
    return propagate(new TIntArrayList(literals));
  }

  /**
   * Removes a list of literals and their negation.
   *
   * u and -u cannot be both in literals.
   *
   */
  private void remove(final TIntArrayList literals) {
    // Finds all nodes that have dirty edges.
    visited.reset();
    int stackTop = 0;
    for (int i = 0; i < literals.size(); i++) {
      int u = literals.get(i);
      for (int j = 0; j < edges(-u).size(); j++) {
        int v = -edges(-u).get(j);
        if (!visited.containsOrAdd(v)) stack[stackTop++] = v;
      }
      for (int j = 0; j < edges(u).size(); j++) {
        int v = -edges(u).get(j);
        if (!visited.containsOrAdd(v)) stack[stackTop++] = v;
      }
    }

    // Marks all literals to be removed.
    visited.reset();
    for (int i = 0; i < literals.size(); i++) {
      int u = literals.get(i);
      assert !visited.contains(u): "Literal " + u + " was already removed";
      visited.add(u);
      visited.add(-u);

      assert EMPTY.isEmpty();
      edges[u + numVariables] = EMPTY;
      edges[-u + numVariables] = EMPTY;
    }

    // Removes unwanted literals from dirty nodes.
    for (int i = 0; i < stackTop; i++) {
      int u = stack[i];

      int p = 0;
      for (int j = 0; j < edges(u).size(); j++) {
        int v = edges(u).get(j);
        if (!visited.contains(v)) {
          edges(u).set(p++, v);
        }
      }

      int removed = edges(u).size() - p;
      if (removed > 0) {
        edges(u).remove(p, removed);
      }
    }
  }

  public void findAllForcedLiterals(final TIntArrayList forced)
      throws ContradictionException {
    for (int u = -numVariables; u <= numVariables; u++) {
      if (u == 0) {
        continue;
      }

      visited.reset();
      internalDFS(u, neg(u));
      if (visited.contains(neg(u))) {
        forced.add(neg(u));
      }
    }

    visited.reset();
    for (int i = 0; i < forced.size(); i++) {
      int literal = forced.getQuick(i);
      visited.add(literal);
      if (visited.contains(neg(literal))) {
        throw new ContradictionException();
      }
    }

    remove(forced);
  }

  /**
   * Finds forced literals and returns a list of assigned literals.
   *
   * Returned units are removed from the graph.
   *
   * This function is faster than findAllContradictions, but find
   * less contradictions because it doesn't recurse.
   *
   * If the graph is transitive closed this is similar to findAllContradictions().
   */
  public void findForcedLiterals(final TIntArrayList forced)
      throws ContradictionException {
    TIntArrayList units = new TIntArrayList();
    for (int u = -numVariables; u <= numVariables; u++) {
      visited.reset();
      visited.add(u);
      for (int i = 0; i < edges(u).size(); i++) {
        int v = edges(u).get(i);
        if (visited.contains(-v)) {
          // If u -> v and u -> -v then -u
          units.add(-u);
          break;
        }
        visited.add(v);
      }
    }

    visited.reset();
    assert forced.isEmpty();
    for (int i = 0; i < units.size(); i++) {
      internalDFS(units.get(i), forced);
    }

    for (int i = 0; i < forced.size(); i++) {
      if (visited.contains(neg(forced.getQuick(i)))) {
        throw new ContradictionException();
      }
    }

    remove(forced);
  }

  /** For backwards compatibility and testing. */
  public TIntArrayList findForcedLiterals() throws ContradictionException {
    TIntArrayList forced = new TIntArrayList();
    findForcedLiterals(forced);
    return forced;
  }

  /**
   * Performs transitive closure.
   *
   * Requires no strongly connected components.
   */
  public void transitiveClosure() {
    topologicalSort();

    // Nodes are visited in topological order therefore
    // every node w in the subtree of child v is already a child of v.
    for (int i = 0; i < topologicalSort.length; i++) {
      int u = topologicalSort[i];
      if (edges(u).isEmpty()) {
        continue;
      }

      visited.reset();
      visited.add(u);
      TIntArrayList all = new TIntArrayList();
      for (int j = 0; j < edges(u).size(); j++) {
        int v = edges(u).get(j);
        if (visited.containsOrAdd(v)) {
          continue;
        }
        all.add(v);

        for (int k = 0; k < edges(v).size(); k++) {
          int w = edges(v).get(k);
          if (!visited.containsOrAdd(w)) {
            all.add(w);
          }
        }
      }
      edges[u + numVariables] = all;
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
      assert edges(u).isEmpty() && edges(-u).isEmpty();
      visited.add(u);
      visited.add(-u);
    }

    TIntHashSet units = new TIntHashSet();
    for (int i = topologicalSort.length - 1; i >= 0; i--) {
      int u = topologicalSort[i];
      if (u == 0 || visited.contains(u)) {
        continue;
      }

      for (int j = 0; j < edges(-u).size(); j++) {
        // v is the parent of u
        int v = -edges(-u).get(j);
        if (units.contains(v)) {
          units.add(u);
          break;
        }
      }

      // u was not forced by any parent so it should be false
      if (!units.contains(u)) {
        units.add(-u);
      }

      visited.add(u);
      visited.add(-u);
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
      internalDFS(topologicalSort[i], component);
      if (component.size() <= 1) {
        continue;
      }

      // Picks the smallest variable as the component name
      // This will pick the same variable on the component with negated literals.
      int best = component.get(0);
      for (int j = 0; j < component.size(); j++) {
        int u = component.get(j);
        if (Math.abs(best) > Math.abs(u)) {
          best = u;
        }
      }

      for (int j = 0; j < component.size(); j++) {
        int u = component.get(j);
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
        // TODO: inefficient toNativeArray()
        edges(get(colapsed, u)).add(edges(u).toNativeArray());
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

  /** Performs a depth first search keeping track of visited nodes. */
  private void internalDFS(int u, TIntArrayList seen) {
    if (visited.containsOrAdd(u)) {
      return;
    }

    int stackTop = 0;
    stack[stackTop++] = u;
    seen.add(u);

    while (stackTop > 0) {
      u = stack[--stackTop];
      for (int i = 0; i < edges(u).size(); i++) {
        int v = edges(u).getQuick(i);
        if (!visited.containsOrAdd(v)) {
          stack[stackTop++] = v;
          seen.add(v);
        }
      }
    }
  }

  /** DFS from u until stop is found.  */
  private void internalDFS(int u, int stop) {
    if (visited.containsOrAdd(u)) {
      return;
    }

    int stackTop = 0;
    stack[stackTop++] = u;

    while (stackTop > 0) {
      u = stack[--stackTop];
      final int size = edges(u).size();
      for (int i = 0; i < size; i++) {
        int v = edges(u).getQuick(i);
        if (!visited.containsOrAdd(v)) {
          if (v == stop) {
            break;
          }
          stack[stackTop++] = v;
        }
      }
    }
  }

  public void dfs(final int start, final TIntArrayList seen) {
    visited.reset();
    for (int i = 0; i < seen.size(); i++) {
      visited.add(seen.getQuick(i));
    }

    internalDFS(start, seen);
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
  private int topologicalSort(int u, int time) {
    if (visited.containsOrAdd(u)) {
      return time;
    }

    for (int i = 0; i < edges(-u).size(); i++) {
      int v = -edges(-u).get(i);
      time = topologicalSort(v, time);
    }

    topologicalSort[time++] = u;
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
        assert edges(-v).contains(-u):
           "Missing reverse edge " + (-v) + " -> " + (-u);
      }
    }

    for (int u = -numVariables; u <= numVariables; u++) {
      assert get(stack, u) == edges(-u).size():
          "Wrong number of edges for literal " + (-u);
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

  public void transitiveReduction() {
    topologicalSort();

    for (int i = 0; i < topologicalSort.length; i++) {
      int u = topologicalSort[i];
      if (u == 0) {
        continue;
      }

      TIntArrayList edges;

      // Removes duplicates starting from end.
      visited.reset();
      edges = edges(u);
      // System.err.println("u = " + u + " edges are " + edges);
      int p = edges.size() - 1;
      for (int j = edges.size() - 1; j >= 0; j--) {
        int v = edges.getQuick(j);
        if (!visited.containsOrAdd(v)) {
          edges.setQuick(p, v);
          p--;
        }
      }
      if (p >= 0) {
        edges.remove(0, p + 1);
      }

      // Puts u at the end of parents.
      // When duplicates in parents' lists are removed, the remaining nodes are in
      // topological order.
      visited.reset();
      edges = edges(neg(u));
      for (int j = 0; j < edges.size(); j++) {
        int v = neg(edges.getQuick(j));
        if (!visited.containsOrAdd(v)) {
          edges(v).add(u);
        }
      }
    }

    for (int i = 0; i < topologicalSort.length; i++) {
      int u = topologicalSort[i];
      if (u == 0) {
        continue;
      }

      visited.reset();

      TIntArrayList edges = edges(u);
      int remaining = 3;
      int p = edges.size() - 1;
      for (int j = edges.size() - 1; j >= 0; j--) {
        int v = edges.getQuick(j);
        if (visited.contains(v)) {
          // Ignores v because it can be reached from u through another node
          continue;
        }

        if (remaining > 0) {
          remaining--;
          visited.add(edges(v));
        }
        edges.setQuick(p, v);
        p--;
      }
      if (p >= 0) {
        edges.remove(0, p + 1);
      }
    }
  }

  /** Returns the graph as a SAT instance */
  public void serialize(final TIntArrayList formula) {
    transitiveReduction();

    for (int i = 0; i < topologicalSort.length; i++) {
      int u = topologicalSort[i];
      if (u == 0) {
        continue;
      }

      TIntArrayList edges = edges(u);
      for (int j = 0; j < edges.size(); j++) {
        int v = edges.getQuick(j);
        assert u != v;

        formula.add(encode(2, OR));
        formula.add(-u);
        formula.add(v);
      }
    }
  }

  private int[] create() {
    return new int[2 * numVariables + 1];
  }

  private int get(int[] array, int u) {
    return array[u + numVariables];
  }

  private void set(int[] array, int u, int v) {
    array[u + numVariables] = v;
  }
}
