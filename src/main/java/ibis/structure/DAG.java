package ibis.structure;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIterator;

import static ibis.structure.BitSet.mapZtoN;

/**
 * Directed acyclic graph of literals.
 */
public final class DAG {
  public static class Join {
    public int parent;
    public TIntHashSet children;

    public Join(final int parent, final TIntHashSet children) {
      this.parent = parent;
      this.children = children;
    }
  }

  // Maps nodes to set of neighbours (including the node itself).
  /** Number of variables. Number of nodes is twice the number of variables. */
  private int numVariables;
  /** The dag. */
  private TIntHashSet[] dag;
  /** Two buffers of numVariables length to reduce memory allocations. */
  private int[] buffer0, buffer1;
  /** Topological sort of dag */
  TIntArrayList[] tsGraph = null;

  /**
   * Creates an empty dag.
   */
  public DAG(final int numVariables) {
    this.numVariables = numVariables;
    dag = new TIntHashSet[2 * numVariables + 1];
    buffer0 = new int[numVariables];
    buffer1 = new int[numVariables];
  }

  /**
   * Returns true if node exists.
   *
   * @param u a literal
   * @return true if dag has node u
   */
  public boolean hasNode(final int u) {
    return dag[BitSet.mapZtoN(u)] != null;
  }

  /**
   * Return an array containing all nodes in the graph.
   *
   * @return all literals with at least one implication
   */
  public int[] nodes() {
    TIntArrayList array = new TIntArrayList();
    for (int literal = -numVariables; literal <= numVariables; ++literal) {
      if (literal != 0) {
        TIntHashSet neighbours = neighbours(literal);
        if (neighbours != null && neighbours.size() > 1) {
          array.add(literal);
        }
      }
    }
    return array.toNativeArray();
  }

  /**
   * Adds a new edge between u and v, joining
   * proxies to maintain the graph acyclic.
   *
   * @param u source literal
   * @param v destination literal
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
    TIntIterator it;

    // Determines new children to propagate from.
    int numChildren = 0;
    int[] children = buffer0;
    it = neighbours(v).iterator();
    for (int size = neighbours(v).size(); size > 0; size--) {
      int literal = it.next();
      if (!neighbours(u).contains(literal)) {
        children[numChildren++] = literal;
      }
    }

    // Determines new parents to propagate to.
    int numParents = 0;
    int[] parents = buffer1;
    it = neighbours(-u).iterator();
    for (int size = neighbours(-u).size(); size > 0; size--) {
      int literal = it.next();
      if (!neighbours(-v).contains(literal)) {
        parents[numParents++] = literal;
      }
    }

    // Connect all parents of u with children of v.
    for (int i = 0; i < numParents; i++) {
      TIntHashSet p = neighbours(-parents[i]);
      if (p.contains(v)) {
        continue;
      }
      for (int j = 0; j < numChildren; j++) {
        p.add(children[j]);
      }
    }
    for (int i = 0; i < numChildren; i++) {
      TIntHashSet c = neighbours(-children[i]);
      if (c.contains(-u)) {
        continue;
      }
      for (int j = 0; j < numParents; j++) {
        c.add(parents[j]);
      }
    }
    
    return null;
  }

  /**
   * Returns all nodes n such that -n => n if edge u, v is added.
   *
   * @param u source literal
   * @param v destination literal
   */
  public TIntHashSet findContradictions(final int u, final int v) {
    TIntHashSet contradictions = new TIntHashSet();
    if (neighbours(u) == null || neighbours(v) == null) {
      if (-u == v) {
        contradictions.add(v);
      }
    } else {
      TIntHashSet neighbours = neighbours(v);
      for (TIntIterator it = neighbours(-u).iterator(); it.hasNext();) {
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
   *
   * @param u node
   * @return a set with all neighbours of u
   */
  public TIntHashSet neighbours(final int u) {
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
        for (TIntIterator it = neighbours.iterator(); it.hasNext();) {
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
   *
   * @param u node to add to graph
   */
  private void createNode(final int u) {
    if (neighbours(u) == null) {
      createNodeHelper(u);
      createNodeHelper(-u);
    }
  }

  /**
   * Creates a new node u.
   *
   * @param u node to add to graph
   */
  private void createNodeHelper(final int u) {
    int u_ = BitSet.mapZtoN(u);
    dag[u_] = new TIntHashSet();
    dag[u_].add(u);
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
   *
   * @param u source vertex
   * @param v destination vertex
   * @return nodes in the cycle
   */
  private Join joinComponents(final int u, final int v) {
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
   *
   * @return assigned literals
   */
  public TIntHashSet solve() {
    final TIntHashSet units = new TIntHashSet();
    for (int literal = -numVariables; literal <= numVariables; ++literal) {
      if (literal != 0 && dag[BitSet.mapZtoN(literal)] != null) {
        if (units.contains(literal) || units.contains(-literal)) {
          continue;
        }

        // Descends until literal has only assigned descendents (if any).
        int root = literal;
        while (true) {
          int next = 0;
          TIntIterator it = neighbours(root).iterator();
          while (it.hasNext()) {
            final int temp = it.next();
            if (temp != root && !units.contains(temp)) {
              next = temp;
              break;
            }
          }

          if (next == 0) {
            break;
          } else {
            root = next;
          }
        }

        // Propagates -literal.
        units.addAll(neighbours(-root).toArray());
      }
    }
    return units;
  }


  /**
   * Produces a topological sort of dag.
   *
   * Stores new graph in tsGraph with nodes sorted in topological order.
   */
  void topologicalSort() {
    int[] queue = new int[2 * numVariables + 1];
    int[] degree = new int[2 * numVariables + 1];
    int queueHead = 0;

    tsGraph = new TIntArrayList[2 * numVariables + 1];
    for (int u = -numVariables; u <= numVariables; ++u) {
      if (u != 0 && neighbours(u) != null) {
        tsGraph[mapZtoN(u)] = new TIntArrayList();
        degree[mapZtoN(u)] = neighbours(u).size() - 1;
        if (degree[mapZtoN(u)] == 0) {
          queue[queueHead++] = u;
        }
      }
    }

    while (queueHead > 0) {
      int u = queue[--queueHead];
      tsGraph[mapZtoN(u)].reverse();

      TIntIterator it = neighbours(-u).iterator();
      for (int size = neighbours(-u).size(); size > 0; size--) {
        int v = -it.next();
        if (v != u) {
          degree[mapZtoN(v)]--;
          tsGraph[mapZtoN(v)].add(u);
          if (degree[mapZtoN(v)] == 0) {
            queue[queueHead++] = v;
          }
        }
      }
    }
  }

  /**
   * Returns an instance containing all edges in the graph.
   *
   * @return a transitive reduction of the implication graph as a SAT instance
   */
  public Skeleton skeleton() {
    topologicalSort();
    Skeleton skeleton = new Skeleton();
    for (int u = -numVariables; u <= numVariables; ++u) {
      if (u != 0 && neighbours(u) != null) {
        TIntArrayList neighbours = tsGraph[mapZtoN(u)];
        for (int i = 0, last = 0; i < neighbours.size(); ++i) {
          int v = tsGraph[mapZtoN(u)].get(i);
          if (-u < v) {
            continue;
          }

          boolean good = last == 0 || !neighbours(last).contains(v);
          for (int j = 0; j < 10 && good; ++j) {
            int w = tsGraph[mapZtoN(u)].get(j);
            if (w == v) {
              break;
            }
            good = !neighbours(w).contains(v);
          }
          if (good) {
            skeleton.add(-u, v);
          }

          last = v;
        }
      }
    }

    // Helps GC.
    tsGraph = null;
    return skeleton;
  }
}
