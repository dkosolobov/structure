package ibis.structure;

import gnu.trove.TIntHashSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class DAGTest {
  private DAG dag = new DAG();
  private DAG.Join join;

  @Test
  public void simpleAdd() throws Exception {
    dag.addEdge(1, 2);
    assertTrue(dag.containsEdge(1, 2));
    assertTrue(dag.containsEdge(-2, -1));

    dag.addEdge(1, 3);
    assertTrue(dag.containsEdge(1, 3));
    assertTrue(dag.containsEdge(-3, -1));

    assertTrue(dag.containsEdge(1, 1));
    assertTrue(dag.containsEdge(2, 2));
    assertTrue(dag.containsEdge(3, 3));
  }

  @Test
  public void transitiveClosure() throws Exception {
    dag.addEdge(1, 2);
    dag.addEdge(2, 3);
    assertTrue(dag.containsEdge(1, 3));
    assertTrue(dag.containsEdge(-3, -1));

    dag.addEdge(4, 5);
    dag.addEdge(5, 6);
    assertTrue(dag.containsEdge(4, 6));
    assertTrue(dag.containsEdge(-6, -4));

    dag.addEdge(3, 4);
    for (int i = 1; i <= 3; ++i)
      for (int j = 4; j <= 6; ++j) {
        assertTrue(dag.containsEdge(i, j));
        assertTrue(dag.containsEdge(-j, -i));
      }
  }

  private void checkJoin(int parent, int... children) {
    assertEquals(join.parent, parent);
    for (int node: children) {
      assertTrue(join.children.contains(node));
      assertFalse(dag.hasNode(node));
    }
  }

  @Test
  public void cycle1() throws Exception {
    dag.addEdge(1, 2);
    dag.addEdge(2, 1);
    assertTrue(dag.hasNode(1));
    assertTrue(dag.hasNode(-1));
    assertFalse(dag.hasNode(2));
    assertFalse(dag.hasNode(-2));
  }

  @Test
  public void cycle3() throws Exception {
    dag.addEdge(1, 2);
    dag.addEdge(2, 3);
    dag.addEdge(3, 4);

    join = dag.addEdge(3, 2);
    checkJoin(2, 3);
    join = dag.addEdge(4, 1);
    checkJoin(1, 2, 4);
  }

  @Test
  public void cycle4() throws Exception {
    dag.addEdge(4, 1);
    dag.addEdge(1, 7);
    dag.addEdge(5, 2);
    dag.addEdge(2, 8);
    dag.addEdge(6, 3);
    dag.addEdge(3, 9);
    dag.addEdge(1, 2);
    dag.addEdge(2, 3);
    join = dag.addEdge(3, 1);
    checkJoin(1, 2, 3);

    for (int i = 4; i <= 6; ++i) {
      for (int j = 7; j <= 9; ++j) {
        assertTrue(dag.containsEdge(i, j));
        assertTrue(dag.containsEdge(-j, -i));
      }
    }
  }

  @Test
  public void cyclePaths1() throws Exception {
    for (int i = 1; i <= 9; ++i) {
      dag.addEdge(i, i + 1);
    }
    join = dag.addEdge(10, 1);
    checkJoin(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
  }

  @Test
  public void contradiction() throws Exception {
    TIntHashSet contradictions = dag.findContradictions(1, -1);
    assertTrue(contradictions.contains(-1));
  }

  @Test
  public void cyclePathsAndContradiction() throws Exception {
    dag.addEdge(3, -2);
    dag.addEdge(2, 1);
    join = dag.addEdge(-2, 3);
    checkJoin(-2, 3);

    TIntHashSet contradictions;
    contradictions = dag.findContradictions(2, -1);
    assertTrue(contradictions.contains(-2));
  }

  @Test
  public void findContradictions1() throws Exception {
    TIntHashSet contradictions;

    contradictions = dag.findContradictions(1, 2);
    assertTrue(contradictions.isEmpty());

    contradictions = dag.findContradictions(1, -1);
    assertTrue(contradictions.contains(-1));

    dag.addEdge(1, 2);
    dag.addEdge(2, 3);
    contradictions = dag.findContradictions(3, -1);
    assertTrue(contradictions.contains(-1));

    dag.addEdge(4, -1);
    contradictions = dag.findContradictions(3, 4);
    assertTrue(contradictions.contains(-1));
  }

  @Test
  public void findContradictions2() throws Exception {
    TIntHashSet contradictions;

    dag.addEdge(1, 4);
    dag.addEdge(2, 4);
    dag.addEdge(3, 4);
    dag.addEdge(5, -1);
    dag.addEdge(5, -2);
    dag.addEdge(5, -3);
    contradictions = dag.findContradictions(4, 5);
    assertTrue(contradictions.contains(-1));
    assertTrue(contradictions.contains(-2));
    assertTrue(contradictions.contains(-3));
    contradictions = dag.findContradictions(-5, -4);
    assertTrue(contradictions.contains(-1));
    assertTrue(contradictions.contains(-2));
    assertTrue(contradictions.contains(-3));
  }

  @Test
  public void findContradictions3() throws Exception {
    TIntHashSet contradictions;

    dag.addEdge(1, 2);
    dag.addEdge(2, 3);
    dag.addEdge(4, -2);
    contradictions = dag.findContradictions(3, 4);
    assertTrue(contradictions.contains(-1));
    assertTrue(contradictions.contains(-2));
  }

  @Test
  public void findContradictions4() throws Exception {
    TIntHashSet contradictions;

    dag.addEdge(2, -1);
    contradictions = dag.findContradictions(1, 2);
    assertTrue(contradictions.contains(-1));
  }

  @Test
  public void delete() throws Exception {
    dag.addEdge(1, 2);
    dag.addEdge(2, 3);

    TIntHashSet u = new TIntHashSet();
    u.add(2);
    dag.delete(u.toArray());
    assertTrue(!dag.hasNode(2));
    assertTrue(dag.containsEdge(1, 3));
    assertTrue(!dag.containsEdge(1, 2));
    assertTrue(!dag.containsEdge(-2, -1));
    assertTrue(!dag.containsEdge(2, 3));
    assertTrue(!dag.containsEdge(-3, -2));
  }

  @Test
  public void simpleClone() throws Exception {
    dag.addEdge(1, 2);
    dag.addEdge(2, 4);

    DAG clone = new DAG(dag);
    assertTrue(clone.containsEdge(-4, -1));
  }
}
