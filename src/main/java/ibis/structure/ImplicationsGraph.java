package ibis.structure;

import gnu.trove.TIntArrayList;

public class ImplicationsGraph {
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
      edges[u] = new TIntArrayList();
    }

    topologicalSort = create();
    colors = create();
    currentColor = 0;
    colapsed = create();
  }

  /** Adds a new implication u &rarr; v. */
  public void add(int u, int v) {
    edges(u).add(v);
    edges(-v).add(-u);
  }

  /** Returns true if implication u &rarrr; v is valid */
  public boolean contains(int u, int v) {
    return edges(u).contains(v);
  }

  /** Returns the edges list for node u. */
  public TIntArrayList edges(int u) {
    return edges[u + numVariables];
  }

  /** Propagates assignment of u and returns assigned literals. */
  public TIntArrayList remove(int u) {
    currentColor++;
    TIntArrayList visited = new TIntArrayList();
    dfs(u, visited);
    remove(visited);
    return visited;
  }

  /** Removes a list of nodes.  */
  private void remove(TIntArrayList nodes) {
    // Detaches nodes from parents
    for (int i = 0; i < nodes.size(); i++) {
      int u = nodes.get(i);
      for (int j = 0; j < edges(-u).size(); j++) {
        int v = -edges(-u).get(j);
        edges(v).remove(edges(v).indexOf(u));
      }
    }
 
    // Detaches -nodes from parents
    for (int i = 0; i < nodes.size(); i++) {
      int u = nodes.get(i);
      for (int j = 0; j < edges(u).size(); j++) {
        int v = -edges(u).get(j);
        edges(v).remove(edges(v).indexOf(-u));
      }
    }

    // Detaches children from nodes
    for (int i = 0; i < nodes.size(); i++) {
      int u = nodes.get(i);
      edges(u).clear();
      edges(-u).clear();
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
      // System.err.println("u = " + u + " edges are " + edges(u));

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
  public TIntArrayList solve(int[] assigned) {
    topologicalSort();
    removeStronglyConnectedComponents();

    currentColor++;
    for (int u : assigned) {
      assert edges(u).isEmpty() && edges(-u).isEmpty();
      visit(u);
    }

    TIntArrayList visited = new TIntArrayList();
    for (int i = topologicalSort.length; i-- > 0;) {
      int u = topologicalSort[i];
      if (!isVisited(u) && !isVisited(-u)) {
        dfs(u, visited);
      }
    }

    return visited;
  }

  /**
   * Finds all strongly connected components.
   *
   * Requires a correct topological sort.
   * Topological is remains correct after components are removed.
   *
   * The implemnted algorithm is described at
   *   http://www.cs.berkeley.edu/~vazirani/s99cs170/notes/lec12.pdf
   */
  public int[] removeStronglyConnectedComponents() {
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

      // System.err.println("component = " + component);

      for (int j = 0; j < component.size(); j++) {
        int u = component.get(j);
        set(colapsed, u, best);
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

    System.err.println("Removed " + removed + " edges");
    return colapsed;
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

  /** Prints the implication graph to stderr. */
  public void print(String message) {
    System.err.println(message);
    for (int u = -numVariables; u <= numVariables; u++) {
      System.err.println(u + " -> " + edges(u));
    }
    System.err.println();
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
