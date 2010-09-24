package ibis.structure;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;

public final class DAG {
  public static class Join {
    public int parent;
    public TIntHashSet children;

    public Join(int parent, TIntHashSet children) {
      this.parent = parent;
      this.children = children;
    }
  }

  // Maps nodes to set of neighbours (including the node itself).
  private TIntObjectHashMap<TIntHashSet> dag;

  /**
   * Creates an empty dag.
   */
  public DAG() {
    dag = new TIntObjectHashMap<TIntHashSet>();
  }

  /**
   * Creates a copy of the given dag.
   */
  public DAG(final DAG other) {
    dag = new TIntObjectHashMap<TIntHashSet>();
    TIntObjectIterator<TIntHashSet> it;
    for (it = other.dag.iterator(); it.hasNext();) {
      it.advance();
      dag.put(it.key(), (TIntHashSet)it.value().clone());
    }
  }

  /**
   * Returns true if node exists.
   */
  public boolean hasNode(final int u) {
    return dag.containsKey(u);
  }

  /**
   * Return an array containing all nodes in the graph.
   */
  public int[] nodes() {
    return dag.keys();
  }

  /**
   * Returns an instance containing all edges in the graph.
   */
  public Skeleton skeleton() {
    Skeleton skeleton = new Skeleton();
    for (TIntObjectIterator<TIntHashSet> itu = dag.iterator(); itu.hasNext();) {
      itu.advance();
      int u = itu.key();
      for (TIntIterator itv = itu.value().iterator(); itv.hasNext();) {
        int v = itv.next();
        if (-u < v && u != v) {
          skeleton.addArgs(-u, v);
        }
      }
    }
    return skeleton;
  }

  /**
   * Adds a new edge between u and v, joining
   * proxies to maintain the graph acyclic.
   */
  public Join addEdge(final int u, final int v) {
    assert u != 0 && v != 0;
    if (containsEdge(u, v)) {
      assert containsEdge(-v, -u);
      return null;
    }
    if (containsEdge(v, u)) {
      assert containsEdge(-u, -v);
      return joinComponents(u, v);
    }

    createNode(u);
    createNode(v);

    // Connect all parents of u with all parents of v.
    int[] children = dag.get(v).toArray();
    int[] parents = dag.get(-u).toArray();
    for (int i = 0; i < parents.length; ++i) {
      dag.get(-parents[i]).addAll(children);
    }
    for (int i = 0; i < children.length; ++i) {
      dag.get(-children[i]).addAll(parents);
    }

    return null;
  }

  /**
   * Returns all nodes n such that -n => n if edge u, v is added.
   */
  public TIntHashSet findContradictions(final int u, final int v) {
    createNode(u);
    createNode(v);

    TIntHashSet contradictions = new TIntHashSet();
    for (TIntIterator it = dag.get(-u).iterator(); it.hasNext(); ) {
      int node = it.next();  // -node => u
      if (containsEdge(v, node)) {  // -node => u => v => node
        contradictions.add(node);
      }
      if (containsEdge(-node, -v)) {  // -node => -v => -u => node
        contradictions.add(node);
      }
    }
    return contradictions;
  }

  /**
   * Returns all nodes of adjacent with the node of u.
   */
  public TIntHashSet neighbours(int u) {
    return dag.get(u);
  }

  /**
   * Deletes all nodes in u_.
   *
   * Constraint: if n in u and n => m then m in u.
   *
   * @param u nodes to remove from graph
   */
  public void delete(final int[] u) {
    for (int a: u) {
      TIntHashSet neighbours = dag.get(-a);
      if (neighbours != null) {
        for (TIntIterator it = neighbours.iterator(); it.hasNext(); ) {
          dag.get(-it.next()).remove(a);
        }
      }
    }

    for (int i = 0; i < u.length; ++i) {
      dag.remove(u[i]);
      dag.remove(-u[i]);
    }
  }

  /**
   * Creates a new nodes u and -u if they don't exist.
   */
  private void createNode(int u) {
    if (!dag.contains(u)) {
      assert !dag.contains(-u);
      createNodeHelper(u);
      createNodeHelper(-u);
    }
  }

  /**
   * Creates a new node u.
   */
  private void createNodeHelper(int u) {
    TIntHashSet neighbours = new TIntHashSet();
    neighbours.add(u);
    dag.put(u, neighbours);
  }

  /**
   * @return true if there is a path between node u and v
   */
  public boolean containsEdge(int u, int v) {
    TIntHashSet neighbours = neighbours(u);
    return neighbours != null && neighbours.contains(v);
  }

  /**
   * If u and v are in the same strongly connected node after
   * the addition of arc (u, v) joins all nodes on the cycle.
   */
  private Join joinComponents(int u, int v) {
    // Finds all compoenents to be replaced by v.
    TIntHashSet replaced = new TIntHashSet();
    for (TIntObjectIterator<TIntHashSet> it = dag.iterator(); it.hasNext();) {
      it.advance();
      if (containsEdge(v, it.key()) && containsEdge(it.key(), u)) {
        replaced.add(it.key());
      }
    }

    // The name of the new node is the smallest node.
    int name = u;
    for (TIntIterator it = replaced.iterator(); it.hasNext();) {
      int node = it.next();
      if (Math.abs(name) > Math.abs(node)) {
        name = node;
      }
    }
    replaced.remove(name);

    // Parents of the new node are parents of u.
    TIntHashSet parents = new TIntHashSet();
    assert dag.containsKey(-u);
    for (TIntIterator it = dag.get(-u).iterator(); it.hasNext();) {
      int node = -it.next();
      if (!replaced.contains(node)) {
        parents.add(node);
      }
    }

    // Children of the new node are children of v.
    TIntHashSet children = new TIntHashSet();
    assert dag.containsKey(v);
    for (TIntIterator it = dag.get(v).iterator(); it.hasNext();) {
      int node = it.next();
      if (!replaced.contains(node)) {
        children.add(node);
      }
    }

    // Connects all parents with all children and .
    for (TIntIterator parent = parents.iterator(); parent.hasNext();) {
      TIntHashSet arcs = dag.get(parent.next());
      for (TIntIterator child = children.iterator(); child.hasNext();) {
        arcs.add(child.next());
      }
      for (TIntIterator node = replaced.iterator(); node.hasNext();) {
        arcs.remove(node.next());
      }
    }
    for (TIntIterator parent = children.iterator(); parent.hasNext();) {
      TIntHashSet arcs = dag.get(-parent.next());
      for (TIntIterator child = parents.iterator(); child.hasNext();) {
        arcs.add(-child.next());
      }
      for (TIntIterator node = replaced.iterator(); node.hasNext();) {
        arcs.remove(-node.next());
      }
    }

    // Removes replaced nodes.
    for (TIntIterator it = replaced.iterator(); it.hasNext();) {
      int node = it.next();
      dag.remove(node);
      dag.remove(-node);
    }

    return new Join(name, replaced);
  }
}
