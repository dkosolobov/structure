package ibis.structure;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntStack;


public class ImplicationsGraph {
  private static final TIntArrayList EMPTY = new TIntArrayList();

  private int numVariables;
  private TIntArrayList[] edges = null;
  private int[] topologicalSort;
  private int[] colors;
  private int currentColor;
  private int[] colapsed;
  private int[] stack;

  public ImplicationsGraph(int numVariables) {
    this.numVariables = numVariables;

    edges = new TIntArrayList[2 * numVariables + 1];
    for (int u = 0; u < 2 * numVariables + 1; u++) {
      edges[u] = EMPTY; // new TIntArrayList();
    }

    topologicalSort = create();
    colors = create();
    currentColor = 0;
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
      edges[-u + numVariables] = new TIntArrayList();
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
    currentColor++;

    TIntArrayList visited = new TIntArrayList();
    for (int i = 0; i < literals.size(); i++) {
      int u = literals.get(i);
      dfs(u, visited);
    }

    for (int i = 0; i < visited.size(); i++) {
      int v = visited.get(i);
      if (isVisited(-v)) {
        throw new ContradictionException();
      }
    }

    remove(visited);
    return visited;
  }

  public TIntArrayList propagate(int... u) throws ContradictionException {
    return propagate(new TIntArrayList(u));
  }

  /**
   * Removes a list of literals and their negation.
   *
   * u and -u cannot be both in literals.
   *
   */
  private void remove(TIntArrayList literals) {
    // Finds all nodes that have dirty edges.
    currentColor++;
    int stackTop = 0;
    for (int i = 0; i < literals.size(); i++) {
      int u = literals.get(i);
      for (int j = 0; j < edges(-u).size(); j++) {
        int v = -edges(-u).get(j);
        if (!visit(v)) stack[stackTop++] = v;
      }
      for (int j = 0; j < edges(u).size(); j++) {
        int v = -edges(u).get(j);
        if (!visit(v)) stack[stackTop++] = v;
      }
    }

    // Marks all literals to be removed.
    currentColor++;
    for (int i = 0; i < literals.size(); i++) {
      int u = literals.get(i);
      assert !isVisited(u);
      visit(u);
      visit(-u);

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
        if (!isVisited(v)) {
          edges(u).set(p++, v);
        }
      }

      int removed = edges(u).size() - p;
      if (removed > 0) {
        edges(u).remove(p, removed);
      }
    }
  }

  /**
   * Finds all contradictions and returns a list of assigned literals.
   *
   * Returned units are removed from the graph.
   */
  public TIntArrayList findAllContradictions() {
    TIntArrayList units = new TIntArrayList();
    for (int u = -numVariables; u <= numVariables; u++) {
      currentColor++;
      dfs(u, -u);
      if (isVisited(-u)) {
        units.add(-u);
      }
    }

    currentColor++;
    TIntArrayList visited = new TIntArrayList();
    for (int i = 0; i < units.size(); i++) {
      int unit = units.get(i);
      if (unit != 0) {
        dfs(unit, visited);
      }
    }

    remove(visited);
    return visited;
  }

  /**
   * Depth first search start at u and stoping at when stop is found.
   */
  private void dfs(int u, int stop) {
    if (visit(u)) {
      return;
    }

    int stackTop = 0;
    stack[stackTop++] = u;

    while (stackTop > 0) {
      u = stack[--stackTop];
      for (int i = 0; i < edges(u).size(); i++) {
        int v = edges(u).getQuick(i);
        if (!visit(v)) {
          if (v == stop) {
            break;
          }
          stack[stackTop++] = v;
        }
      }
    }
  }

  /**
   * Finds contradictions and returns a list of assigned literals.
   *
   * Returned units are removed from the graph.
   *
   * This function is faster than findAllContradictions, but find
   * less contradictions because it doesn't recurse.
   *
   * If the graph is transitive closed this is similar to findAllContradictions().
   */
  public TIntArrayList findContradictions() {
    currentColor++;
    TIntArrayList visited = new TIntArrayList();
    for (int u = -numVariables; u <= numVariables; u++) {
      if (!isVisited(-u) && contains(u, -u)) {
        dfs(-u, visited);
      }
    }

    remove(visited);
    return visited;
  }

  /** Returns a score of density of the graph. */
  public double density() {
    int n = 0, m = 0;
    for (int u = -numVariables; u <= numVariables; u++) {
      if (edges(u) == EMPTY) continue;
      n += 1;
      m += edges(u).size();
    }
    return Math.log(m) / Math.log(n);
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

      currentColor++;
      visit(u);
      TIntArrayList all = new TIntArrayList();
      for (int j = 0; j < edges(u).size(); j++) {
        int v = edges(u).get(j);
        if (visit(v)) {
          continue;
        }
        all.add(v);

        for (int k = 0; k < edges(v).size(); k++) {
          int w = edges(v).get(k);
          if (!visit(w)) {
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
  public TIntArrayList solve(final TIntArrayList assigned)
      throws ContradictionException {
    currentColor++;
    for (int i = 0; i < assigned.size(); i++) {
      int u = assigned.get(i);
      assert edges(u).isEmpty() && edges(-u).isEmpty();
      visit(u);
    }

    TIntArrayList visited = new TIntArrayList();
    for (int i = topologicalSort.length; i-- > 0;) {
      int u = topologicalSort[i];
      if (u != 0 && !isVisited(u) && !isVisited(-u)) {
        dfs(u, visited);
      }
    }

    for (int u = 1; u <= numVariables; u++) {
      if (isVisited(u) && isVisited(-u)) {
        throw new ContradictionException();
      }
    }

    return visited;
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

    currentColor++;
    TIntArrayList component = new TIntArrayList();
    for (int i = 0; i < topologicalSort.length; i++) {
      component.reset();
      dfs(topologicalSort[i], component);
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

      currentColor++;
      visit(u);
      int pos = 0;
      for (int j = 0; j < edges(u).size(); j++) {
        int v = get(colapsed, edges(u).getQuick(j));
        if (!visit(v)) {
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
  private void dfs(int u, TIntArrayList visited) {
    if (visit(u)) {
      return;
    }

    int stackTop = 0;
    stack[stackTop++] = u;
    visited.add(u);

    while (stackTop > 0) {
      u = stack[--stackTop];
      for (int i = 0; i < edges(u).size(); i++) {
        int v = edges(u).getQuick(i);
        if (!visit(v)) {
          stack[stackTop++] = v;
          visited.add(v);
        }
      }
    }
  }

  /** Does a topological sort and stores it in topologicalSort array. */
  public void topologicalSort() {
    currentColor++;
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
    if (visit(u)) {
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
  private void verify() {
    String error = verifyToString();
    assert error == null : error;
  }

  private String verifyToString() {
    for (int u = -numVariables; u <= numVariables; u++) {
      if (get(colors, u) >= currentColor) {
        return "Wrong color for literal " + u;
      }
    }

    for (int u = -numVariables; u <= numVariables; u++) {
      for (int i = 0; i < edges(u).size(); i++) {
        int v = edges(u).get(i);
        if (!edges(-v).contains(-u)) {
          return "Missing reverse edge " + (-v) + " -> " + (-u);
        }
      }
    }
    return null;
  }


  public String toString() {
    StringBuffer buffer = new StringBuffer();
    for (int u = -numVariables; u <= numVariables; u++) {
      buffer.append(u + " -> " + edges(u) + "\n");
    }
    return buffer.toString();
  }

  /** Prints the implication graph to stderr. */
  public void print(String message) {
    System.err.println(message);
    System.err.println(toString());
  }

  /** Returns the graph as a SAT instance */
  public Skeleton skeleton() {
    Skeleton skeleton = new Skeleton();
    findStronglyConnectedComponents();

    // TODO: simplify
    for (int u = -numVariables; u <= numVariables; u++) {
      int u_ = get(colapsed, u);
      for (int i = 0; i < edges(u).size(); i++) {
        int v = edges(u).get(i);
        int v_ = get(colapsed, v);

        if (-u > v) {
          continue;
        }

        boolean good = true;
        for (int j = Math.min(i - 1, 9); good && j >= 0; j--) {
          int w = edges(u).get(j);
          int w_ = get(colapsed, w);
          good = w_ == u_ || w_ == v_ || !contains(w, v);
        }
        for (int j = Math.max(i - 5, 0); good && j < i; j++) {
          int w = edges(u).get(j);
          int w_ = get(colapsed, w);
          good = w_ == u_ || w_ == v_ || !contains(w, v);
        }
        for (int j = Math.min(i + 5, edges(u).size() - 1); good && j > i; j--) {
          int w = edges(u).get(j);
          int w_ = get(colapsed, w);
          good = w_ == u_ || w_ == v_ || !contains(w, v);
        }

        if (good) {
          skeleton.add(-u, v);
        }
      }
    }
    return skeleton;
  }

  /** Returns true if node is painted with current color otherwise paint it. */
  private boolean visit(int u) {
    assert get(colors, u) <= currentColor;
    if (get(colors, u) == currentColor) {
      return true;
    }
    set(colors, u, currentColor);
    return false;
  }

  private boolean isVisited(int u) {
    return get(colors, u) == currentColor;
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
