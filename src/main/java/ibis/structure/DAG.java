package ibis.structure;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIterator;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectIterator;
import gnu.trove.TIntObjectHashMap;

public class DAG {
  public static class Join {
    public int component;
    public TIntHashSet components;

    public Join(int component, TIntHashSet components) {
      this.component = component;
      this.components = components;
    }
  }

  // The set of strongly connected components components.
  // The elements in this set is included in the keys of proxies.
  private TIntHashSet components;
  // Maps nodes to the component they belong.
  // If a key is in components then the value associated equals the key.
  private TIntIntHashMap proxies;
  // Maps proxies to set of neighbours.
  private TIntObjectHashMap<TIntHashSet> dag;

  public DAG() {
    components = new TIntHashSet();
    proxies = new TIntIntHashMap();
    dag = new TIntObjectHashMap<TIntHashSet>();
  }

  public DAG(DAG other) {
    components = (TIntHashSet)other.components.clone();
    proxies = (TIntIntHashMap)other.proxies.clone();
    dag = new TIntObjectHashMap<TIntHashSet>();

    TIntIterator it;
    for (it = components.iterator(); it.hasNext();) {
      int node = it.next();
      dag.put(node, (TIntHashSet)other.dag.get(node).clone());
    }
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
    for (int u: proxies.keys()) {
      int v = component(u);
      if (u != v) {
        skeleton.addArgs(u, -v);
        skeleton.addArgs(-u, v);
      }
    }
    return skeleton;
  }

  // @return true if there is a path between u and v
  public boolean containsEdge(int u, int v) {
    return containsComponentEdge(component(u), component(v));
  }

  // Adds a new edge between u and v, joining
  // proxies to maintain the graph acyclic,
  public Join addEdge(int u, int v) {
    u = component(u);
    v = component(v);

    if (containsComponentEdge(u, v)) {
      return null;
    }
    if (containsComponentEdge(v, u)) {
      assert containsComponentEdge(-u, -v);
      return joinComponents(u, v);
    }

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

  // Returns all components n such that -n => n if edge u, v is added.
  public TIntHashSet findContradictions(int u, int v) {
    u = component(u);
    v = component(v);

    TIntHashSet contradictions = new TIntHashSet();
    TIntIterator it;
    for (it = dag.get(-u).iterator(); it.hasNext(); ) {
      int node = it.next();  // -node => u
      if (containsComponentEdge(v, node)) {
        // -node => u => v => node
        contradictions.add(node);
      }
      if (containsComponentEdge(-node, -v)) {
        // -node => -v => -u => node
        contradictions.add(node);
      }
    }

    return contradictions;
  }

  public TIntHashSet edges(int u) {
    return dag.get(component(u));
  }

  /**
   * @return the component of node u.
   * If n is a new node a component is created for it.
   */
  public int component(int u) {
    if (proxies.contains(u)) {
      return findComponent(u);
    }
    createComponent(u);
    createComponent(-u);
    return u;
  }

  // @return the set of components
  public TIntHashSet components() {
    return components;
  }

  // @return true if u is a component.
  public boolean hasComponent(int component) {
    return components.contains(component);
  }

  // Deletes all components in u_.
  public void delete(TIntHashSet u_) {
    int u[] = u_.toArray();
    int non_u[] = u_.toArray();
    for (int i = 0; i < non_u.length; ++i) {
      non_u[i] = -non_u[i];
    }

    components.removeAll(u);
    components.removeAll(non_u);
    for (int i = 0; i < u.length; ++i) {
      proxies.remove(u[i]);
      proxies.remove(non_u[i]);
    }

    TIntIterator it;
    for (it = components.iterator(); it.hasNext();) {
      TIntHashSet arcs = dag.get(it.next());
      arcs.removeAll(u);
      arcs.removeAll(non_u);
    }
  }

  private int findComponent(int u) {
    int v = proxies.get(u);
    if (u != v) {
      System.out.println("v = " + v);
      v = findComponent(v);
      proxies.put(u, v);
    }
    return v;
  }

  // Creates two new components u and -u.
  private void createComponent(int u) {
    assert !components.contains(u) && !proxies.containsKey(u);
    components.add(u);
    proxies.put(u, u);
    TIntHashSet arcs = new TIntHashSet();
    arcs.add(u);
    dag.put(u, arcs);
  }

  // @return true if there is a path between proxies u and v
  private boolean containsComponentEdge(int u, int v) {
    assert dag.containsKey(u);
    return dag.get(u).contains(v);
  }

  // If u and v are in the same strongly connected component after
  // the addition of arc (u, v) joins all proxies on the cycle.
  private Join joinComponents(int u, int v) {
    assert containsComponentEdge(v, u);
    TIntIterator it;

    // Finds all proxies to be replaced by v.
    TIntHashSet replaced = new TIntHashSet();
    for (it = components.iterator(); it.hasNext();) {
      int node = it.next();
      if (containsComponentEdge(v, node) && containsComponentEdge(node, u)) {
        replaced.add(node);
      }
    }

    // The name of the new component is the smallest component.
    int name = u;
    for (it = replaced.iterator(); it.hasNext();) {
      int node = it.next();
      if (Math.abs(name) > Math.abs(node)) {
        name = node;
      }
    }
    replaced.remove(name);

    // Parents of the new component are parents of u.
    TIntHashSet parents = new TIntHashSet();
    assert dag.containsKey(-u);
    for (it = dag.get(-u).iterator(); it.hasNext();) {
      int node = -it.next();
      if (!replaced.contains(node)) {
        parents.add(node);
      }
    }

    // Children of the new component are children of v.
    TIntHashSet children = new TIntHashSet();
    assert dag.containsKey(v);
    for (it = dag.get(v).iterator(); it.hasNext();) {
      int node = it.next();
      if (!replaced.contains(node)) {
        children.add(node);
      }
    }

    System.out.println("name = " + name +
                       " list = " + (new TIntArrayList(replaced.toArray())));


    // Connects all parents with all children and removes replaced proxies.
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

    // Joins all components.
    for (it = replaced.iterator(); it.hasNext();) {
      int node = it.next();
      components.remove(node);
      dag.remove(node);
      proxies.put(node, name);

      components.remove(-node);
      dag.remove(-node);
      proxies.put(-node, -name);
    }

    return new Join(name, replaced);
  }
}
