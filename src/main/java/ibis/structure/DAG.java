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
   * Returns the number of nodes in the graph.
   */
  public int numNodes() {
    return dag.size();
  }

  /**
   * Returns the sum of square of out degrees.
   */
  public int sumSquareDegrees() {
    int size = 0;
    TIntObjectIterator<TIntHashSet> it;
    for (it = dag.iterator(); it.hasNext();) {
      it.advance();
      size += (it.value().size() - 1) * (it.value().size() - 1);
    }
    return size;
  }

  /**
   * Returns the number of edges in the graph.
   */
  public int numEdges() {
    int size = 0;
    TIntObjectIterator<TIntHashSet> it;
    for (it = dag.iterator(); it.hasNext();) {
      it.advance();
      size += it.value().size() - 1;
    }
    return size;
  }

  /**
   * Returns an instance containing all edges in the graph.
   */
  public Skeleton skeleton() {
    Skeleton skeleton = new Skeleton();
    for (TIntObjectIterator<TIntHashSet> itu = dag.iterator(); itu.hasNext();) {
      itu.advance();
      final int u = itu.key();
      final int[] neighbours = itu.value().toArray();

      for (int v: neighbours) {
        if (!(-u < v && u != v)) {
          continue;
        }

        boolean redundant = false;
        for (int w: neighbours) {
          if (w != v && w != u && dag.get(w).contains(v)) {
            redundant = true;
            break;
          }
        }

        if (!redundant) {
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
      return null;
    }
    if (containsEdge(v, u)) {
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
    TIntHashSet contradictions = new TIntHashSet();
    if (!dag.containsKey(u) || !dag.containsKey(v)) {
      if (-u == v) {
        contradictions.add(v);
      }
    } else {
      TIntHashSet neighbours = dag.get(v);
      for (TIntIterator it = dag.get(-u).iterator(); it.hasNext(); ) {
        int node = it.next();  // -node => u
        if (neighbours.contains(node)) {  // -node => u => v => node
          contradictions.add(node);
        }
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
    // Finds all components to be replaced.
    TIntHashSet replaced = new TIntHashSet();
    for (TIntIterator it = dag.get(v).iterator(); it.hasNext();) {
      int node = it.next();
      if (dag.get(node).contains(u)) {
        replaced.add(node);
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

  /**
   * Returns the units after solving the 2SAT encoded in this DAG.
   */
  public TIntHashSet solve() {
    final TIntHashSet units = new TIntHashSet();
    final int[] nodes = dag.keys();

    for (int node: nodes) {
      if (units.contains(node) || units.contains(-node)) {
        continue;
      }

      // Descends until node has only assigned descendents (if any).
      while (true) {
        int next = 0;
        TIntIterator it = dag.get(node).iterator();
        while (it.hasNext()) {
          final int temp = it.next();
          if (units.contains(-temp)) {
            System.err.println("BULLSHIT ****************");
            System.exit(1);
          }
          if (temp != node && !units.contains(temp)) {
            next = temp;
            break;
          }
        }

        if (next == 0) {
          break;
        } else {
          node = next;
        }
      }

      // Propagates -node.
      units.addAll(dag.get(-node).toArray());
    }
    return units;
  }
}
