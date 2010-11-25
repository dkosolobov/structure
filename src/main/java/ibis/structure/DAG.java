package ibis.structure;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntArrayList;
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
  private int numVariables;
  private TIntHashSet[] dag;

  /**
   * Creates an empty dag.
   */
  public DAG(final int numVariables_) {
    numVariables = numVariables_;
    dag = new TIntHashSet[2 * numVariables + 1];
  }

  /**
   * Returns true if node exists.
   */
  public boolean hasNode(final int u) {
    return dag[BitSet.mapZtoN(u)] != null;
  }

  /**
   * Return an array containing all nodes in the graph.
   */
  public int[] nodes() {
    TIntArrayList array = new TIntArrayList();
    for (int literal = -numVariables; literal <= numVariables; ++literal) {
      if (literal != 0) {
        TIntHashSet neighbours = dag[BitSet.mapZtoN(literal)];
        if (neighbours != null && neighbours.size() > 1) {
          array.add(literal);
        }
      }
    }
    return array.toNativeArray();
  }

  /**
   * Returns an instance containing all edges in the graph.
   */
  public Skeleton skeleton() {
    Skeleton skeleton = new Skeleton();
    for (int u = -numVariables; u <= numVariables; ++u) {
      if (u != 0 && dag[BitSet.mapZtoN(u)] != null) {
        final int[] neighbours = dag[BitSet.mapZtoN(u)].toArray();

        for (int v: neighbours) {
          if (!(-u < v && u != v)) {
            continue;
          }

          // Transitive reduction
          boolean redundant = false;
          for (int w : neighbours) {
            if (w != v && w != u && dag[BitSet.mapZtoN(w)].contains(v)) {
              redundant = true;
              break;
            }
          }

          if (!redundant) {
            skeleton.add(-u, v);
          }
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
    int[] children = neighbours(v).toArray();
    int[] parents = neighbours(-u).toArray();
    for (int i = 0; i < parents.length; ++i) {
      neighbours(-parents[i]).addAll(children);
    }
    for (int i = 0; i < children.length; ++i) {
      neighbours(-children[i]).addAll(parents);
    }

    return null;
  }

  /**
   * Returns all nodes n such that -n => n if edge u, v is added.
   */
  public TIntHashSet findContradictions(final int u, final int v) {
    TIntHashSet contradictions = new TIntHashSet();
    if (neighbours(u) == null || neighbours(v) == null) {
      if (-u == v) {
        contradictions.add(v);
      }
    } else {
      TIntHashSet neighbours = neighbours(v);
      for (TIntIterator it = neighbours(-u).iterator(); it.hasNext(); ) {
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
    return dag[BitSet.mapZtoN(u)];
  }

  /**
   * Deletes all nodes in u_.
   *
   * Constraint: if n in u and n => m then m in u.
   *
   * @param u nodes to remove from graph
   */
  public void delete(final int[] units) {
    for (int literal : units) {
      TIntHashSet neighbours = neighbours(-literal);
      if (neighbours != null) {
        for (TIntIterator it = neighbours.iterator(); it.hasNext(); ) {
          neighbours(-it.next()).remove(literal);
        }
      }
    }

    for (int literal : units) {
      dag[BitSet.mapZtoN(literal)] = null;
      dag[BitSet.mapZtoN(-literal)] = null;
    }
  }

  /**
   * Creates a new nodes u and -u if they don't exist.
   */
  private void createNode(int u) {
    if (neighbours(u) == null) {
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
    dag[BitSet.mapZtoN(u)] = neighbours;
  }

  /**
   * @return true if there is a path between node u and v
   */
  public boolean containsEdge(final int u, final int v) {
    int u_ = BitSet.mapZtoN(u);
    return dag[u_] != null && dag[u_].contains(v);
  }

  /**
   * If u and v are in the same strongly connected node after
   * the addition of arc (u, v) joins all nodes on the cycle.
   */
  private Join joinComponents(int u, int v) {
    // Finds all components to be replaced.
    TIntHashSet replaced = new TIntHashSet();
    for (TIntIterator it = neighbours(v).iterator(); it.hasNext();) {
      int node = it.next();
      if (neighbours(node).contains(u)) {
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
    for (TIntIterator it = neighbours(-u).iterator(); it.hasNext();) {
      int node = -it.next();
      if (!replaced.contains(node)) {
        parents.add(node);
      }
    }

    // Children of the new node are children of v.
    TIntHashSet children = new TIntHashSet();
    for (TIntIterator it = neighbours(v).iterator(); it.hasNext();) {
      int node = it.next();
      if (!replaced.contains(node)) {
        children.add(node);
      }
    }

    // Connects all parents with all children and .
    for (TIntIterator parent = parents.iterator(); parent.hasNext();) {
      TIntHashSet arcs = neighbours(parent.next());
      for (TIntIterator child = children.iterator(); child.hasNext();) {
        arcs.add(child.next());
      }
      for (TIntIterator node = replaced.iterator(); node.hasNext();) {
        arcs.remove(node.next());
      }
    }
    for (TIntIterator parent = children.iterator(); parent.hasNext();) {
      TIntHashSet arcs = neighbours(-parent.next());
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
      dag[BitSet.mapZtoN(node)] = null;
      dag[BitSet.mapZtoN(-node)] = null;
    }

    return new Join(name, replaced);
  }

  /**
   * Returns the units after solving the 2SAT encoded in this DAG.
   */
  public TIntHashSet solve() {
    final TIntHashSet units = new TIntHashSet();
    for (int literal = -numVariables; literal <= numVariables; ++literal) {
      if (literal != 0 && dag[BitSet.mapZtoN(literal)] != null) {
        if (units.contains(literal) || units.contains(-literal)) {
          continue;
        }

        // Descends until literal has only assigned descendents (if any).
        while (true) {
          int next = 0;
          TIntIterator it = neighbours(literal).iterator();
          while (it.hasNext()) {
            final int temp = it.next();
            if (temp != literal && !units.contains(temp)) {
              next = temp;
              break;
            }
          }

          if (next == 0) {
            break;
          } else {
            literal = next;
          }
        }

        // Propagates -literal.
        units.addAll(neighbours(-literal).toArray());
      }
    }
    return units;
  }
}
