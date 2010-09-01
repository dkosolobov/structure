import gnu.trove.TIntHashSet;
import junit.framework.TestCase;

public class DAGTest extends TestCase {
  DAG dag = new DAG();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testSimpleAdd() throws Exception {
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

  public void testTransitiveClosure() throws Exception {
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

  public void testCycle1() throws Exception {
    dag.addEdge(1, 2);
    dag.addEdge(2, 1);
    assertTrue(dag.hasComponent(1));
    assertTrue(dag.hasComponent(-1));
    assertTrue(dag.hasComponent(1));
    assertTrue(dag.hasComponent(-1));
    assertEquals(1, dag.component(2));
    assertEquals(-1, dag.component(-2));
  }

  public void testCycle2() throws Exception {
    dag.addEdge(2, 1);
    dag.addEdge(1, 2);
    assertTrue(dag.hasComponent(1));
    assertTrue(dag.hasComponent(-1));
    assertTrue(!dag.hasComponent(2));
    assertTrue(!dag.hasComponent(-2));
    assertEquals(1, dag.component(2));
    assertEquals(-1, dag.component(-2));
  }

  public void testCycle3() throws Exception {
    dag.addEdge(1, 2);
    dag.addEdge(2, 3);
    dag.addEdge(3, 4);

    dag.addEdge(3, 2);
    assertEquals(2, dag.component(3));

    dag.addEdge(4, 1);
    assertEquals(1, dag.component(2));
    assertEquals(-1, dag.component(-2));
    assertEquals(1, dag.component(3));
    assertEquals(-1, dag.component(-3));
    assertEquals(1, dag.component(4));
    assertEquals(-1, dag.component(-4));
  }

  public void testCycle4() throws Exception {
    dag.addEdge(4, 1);
    dag.addEdge(1, 7);
    dag.addEdge(5, 2);
    dag.addEdge(2, 8);
    dag.addEdge(6, 3);
    dag.addEdge(3, 9);
    dag.addEdge(1, 2);
    dag.addEdge(2, 3);
    dag.addEdge(3, 1);

    assertEquals(1, dag.component(1));
    assertEquals(1, dag.component(2));
    assertEquals(1, dag.component(3));

    for (int i = 4; i <= 6; ++i) {
      for (int j = 7; j <= 9; ++j) {
        assertTrue(dag.containsEdge(i, j));
        assertTrue(dag.containsEdge(-j, -i));
      }
    }
  }

  public void testContradiction() throws Exception {
    dag.addEdge(1, -1);
    assertEquals(1, dag.component(1));
    assertEquals(-1, dag.component(-1));
    assertTrue(dag.containsEdge(1, -1));
  }

  public void testCyclePaths() throws Exception {
    for (int i = 1; i <= 9; ++i) {
      dag.addEdge(i, i + 1);
    }
    dag.addEdge(10, 1);

    for (int i = 1; i <= 10; ++i) {
      assertEquals(1, dag.component(i));
      assertEquals(-1, dag.component(-i));
      for (int j = 1; j <= 10; ++j) {
        assertTrue(dag.containsEdge(i, j));
        assertTrue(dag.containsEdge(-i, -j));
      }
    }
  }

  public void testFindContradictions1() throws Exception {
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

  public void testFindContradictions2() throws Exception {
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

  public void testFindContradictions3() throws Exception {
    TIntHashSet contradictions;

    dag.addEdge(1, 2);
    dag.addEdge(2, 3);
    dag.addEdge(4, -2);
    contradictions = dag.findContradictions(3, 4);
    assertTrue(contradictions.contains(-1));
    assertTrue(contradictions.contains(-2));
  }

  public void testFindContradictions4() throws Exception {
    TIntHashSet contradictions;

    dag.addEdge(2, -1);
    contradictions = dag.findContradictions(1, 2);
    assertTrue(contradictions.contains(-1));
  }

  public void testDelete() throws Exception {
    dag.addEdge(1, 2);
    dag.addEdge(2, 3);

    TIntHashSet u = new TIntHashSet();
    u.add(2);
    dag.delete(u);
    assertTrue(!dag.hasComponent(2));
    assertTrue(dag.containsEdge(1, 3));
    assertTrue(!dag.containsEdge(1, 2));
    assertTrue(!dag.containsEdge(-2, -1));
    assertTrue(!dag.containsEdge(2, 3));
    assertTrue(!dag.containsEdge(-3, -2));
  }

  public void testClone() throws Exception {
    dag.addEdge(1, 2);
    dag.addEdge(2, 4);

    DAG clone = new DAG(dag);
    assertTrue(clone.containsEdge(-4, -1));
  }
}
