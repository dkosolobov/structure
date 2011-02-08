package ibis.structure;

import gnu.trove.TIntArrayList;

public class ImplicationsGraph {
  private static final TIntArrayList EMPTY = new TIntArrayList();

  private int numVariables;
  private TIntArrayList[] edges = null;
  private int[] topologicalSort;
  private int[] colors;
  private int currentColor;
  private int[] colapsed;

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
    for (int i = 0; i < literals.size(); i++) {
      int u = literals.get(i);
      assert !literals.contains(-u);

      TIntArrayList children = edges(u);
      TIntArrayList parents = edges(-u);

      assert EMPTY.isEmpty();
      edges[u + numVariables] = EMPTY;
      edges[-u + numVariables] = EMPTY;

      for (int j = 0; j < parents.size(); j++) {
        int v = -parents.get(j);
        if (v != u && v != -u) {
          edges(v).remove(edges(v).indexOf(u));
        }
      }

      for (int j = 0; j < children.size(); j++) {
        int v = -children.get(j);
        if (v != u && v != -u) {
          edges(v).remove(edges(v).indexOf(-u));
        }
      }
    }
  }

  /**
   * Finds contradictions and returns a list of assigned literals.
   *
   * Returned units are removed from the graph.
   *
   * Requires transitive closure.
   */
  public TIntArrayList findContradictions() {
    currentColor++;
    TIntArrayList visited = new TIntArrayList();
    for (int u = -numVariables; u <= numVariables; u++) {
      if (contains(u, -u)) {
        dfs(-u, visited);
      }
    }

    remove(visited);
    return visited;
  }

  /**
   * Performs transitive closure.
   *
   * Requires correct topological sort and no strongly connected components.
   */
  public void transitiveClosure() {
    // Nodes are visited in topological order therefore
    // every node w in the subtree of child v is already a child of v.
    for (int i = 0; i < topologicalSort.length; i++) {
      int u = topologicalSort[i];
      if (edges(u).isEmpty()) {
        continue;
      }

      TIntArrayList all = new TIntArrayList();
      currentColor++;
      visit(u);
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
   */
  public TIntArrayList solve(TIntArrayList assigned) throws ContradictionException {
    topologicalSort();
    removeStronglyConnectedComponents();

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
      component.clear();
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
   *
   * Requires a correct topological sort.
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
        edges(u).clear();
        continue;
      }
    }

    int removed = 0;
    for (int u = -numVariables; u <= numVariables; u++) {
      if (get(colapsed, u) != u) {
        continue;
      }

      currentColor++;
      visit(u);
      int pos = 0;
      for (int j = 0; j < edges(u).size(); j++) {
        int v = get(colapsed, edges(u).get(j));
        if (!visit(v)) {
          edges(u).set(pos++, v);
        }
      }
      removed += edges(u).size() - pos;
      if (pos != edges(u).size()) {
        edges(u).remove(pos, edges(u).size() - pos);
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
    visited.add(u);

    for (int i = 0; i < edges(u).size(); i++) {
      int v = edges(u).get(i);
      dfs(v, visited);
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

    // System.err.println("Topological sort is " + (new TIntArrayList(topologicalSort)));
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

  private String verify_() {
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

  private void verify() {
    String error = verify_();
    assert error == null : error;
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
