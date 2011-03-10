package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class ImplicationsGraphTest {
  ImplicationsGraph graph;

  @Test
  public void addImplication() {
    create(3);

    add(1, 3);
    assertTrue(graph.contains(1, 3));
    assertTrue(graph.contains(-3, -1));

    add(3, 3);
    assertTrue(graph.contains(3, 3));
    assertTrue(graph.contains(-3, -3));

    add(-3, 3);
    assertTrue(graph.contains(-3, 3));
    assertFalse(graph.contains(3, -3));
  }

  @Test
  public void stronglyConnectedComponents()
      throws ContradictionException {
    create(6);
    int[] colapsed;

    add(1, 2, 3);
    add(2, 1);
    add(3, 4, 5);
    add(4, 5, 6);
    add(5, 6);
    add(6, 4);
    graph.topologicalSort();
    colapsed = graph.removeStronglyConnectedComponents();
    compare(colapsed, 0, 1, 1, 3, 4, 4, 4);

    add(4, 3);
    graph.topologicalSort();
    colapsed = graph.removeStronglyConnectedComponents();
    compare(colapsed, 0, 1, 2, 3, 3, 5, 6);
  }

  @Test(expected=ContradictionException.class)
  public void stronglyConnectedComponentsContradiction()
      throws ContradictionException {
    create(3);

    add(-1, 3);
    add(3, 2);
    add(2, 1);
    add(1, -1);
    graph.topologicalSort();
    graph.removeStronglyConnectedComponents();
  }

  @Test
  public void propagate() throws ContradictionException {
    int[] propagated;

    create(6);
    add(1, 2, 3);
    propagated = graph.propagate(1).toArray();
    assertFalse(graph.contains(1, 2));
    assertFalse(graph.contains(-2, -1));
    assertFalse(graph.contains(1, 3));
    assertFalse(graph.contains(-3, -1));
    compare(propagated, 1, 2, 3);

    create(6);
    add(1, 2);
    propagated = graph.propagate(2).toArray();
    assertFalse(graph.contains(1, 2));
    assertFalse(graph.contains(-2, -1));
    compare(propagated, 2);

    create(6);
    add(5, 1);
    add(1, 2, 3);
    add(2, 4);
    add(3, 4);
    add(4, 1);
    propagated = graph.propagate(4).toArray();
    assertFalse(graph.contains(1, 2));
    assertFalse(graph.contains(-2, -1));
    assertFalse(graph.contains(1, 3));
    assertFalse(graph.contains(-3, -1));
    assertFalse(graph.contains(5, 1));
    assertFalse(graph.contains(-1, -5));
    compare(propagated, 4, 1, 2, 3);

    create(6);
    add(5, 1);
    add(1, 2);
    add(2, 3);
    add(3, 4);
    add(4, 5);
    add(5, 1);
    add(1, 2);
    add(2, 3);
    add(3, 4);
    add(4, 5);
    propagated = graph.propagate(3).toArray();
    compare(propagated, 3, 4, 5, 1, 2);
  }

  @Test(expected=ContradictionException.class)
  public void propagateContradiction() throws ContradictionException {
    create(4);

    add(-1, 1);
    add(1, 2);
    add(2, 3);
    add(3, 4);
    add(4, -1);
    graph.propagate(3);
  }

  private void create(int numVariables) {
    graph = new ImplicationsGraph(numVariables);
  }

  private void add(int source, int... edges) {
    for (int i = 0; i < edges.length; i++) {
      graph.add(source, edges[i]);
    }
  }

  private void compare(int[] array, int... elements) {
    assertEquals(new TIntArrayList(elements), new TIntArrayList(array));
  }
}
