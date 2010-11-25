package ibis.structure;

import org.junit.Test;
import gnu.trove.TIntArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SolverTest {
  private Solver solver;

  @Test(expected=ContradictionException.class)
  public void contradiction1() throws Exception {
    parse("p cnf 1 2\n1 0 -1 0");
  }

  @Test(expected=ContradictionException.class)
  public void contradiction2() throws Exception {
    parse("p cnf 2 3\n1 0 2 0 -1 -2 0");
  }

  @Test(expected=ContradictionException.class)
  public void contradiction3() throws Exception {
    parse("p cnf 3 4\n1 0 2 0 3 0 -1 -2 -3 0");
  }

  @Test(expected=ContradictionException.class)
  public void contradiction4() throws Exception {
    parse("p cnf 3 4\n1 0 2 0 -1 -2 -3 0 3 0");
  }

  @Test(expected=ContradictionException.class)
  public void contradiction5() throws Exception {
    parse("p cnf 1 2\n-1 0 1 1 0");
  }

  @Test(expected=ContradictionException.class)
  public void contradiction6() throws Exception {
    parse("p cnf 3 4\n1 -2 0 1 -3 0 1 2 3 0 -1 0");
  }

  @Test
  public void propagate1() throws Exception {
    parse("p cnf 1 4\n1 0 1 -1 0 1 1 0 1 -1 -1 0");
    compare(solver, 1, 0);
  }

  @Test
  public void propagate2() throws Exception {
    parse("p cnf 3 3\n1 -1 2 0 1 1 1 0 -2 -2 -3 0");
    compare(solver, 1, 0, -2, -3, 0);
  }

  @Test
  public void propagate3() throws Exception {
    parse("p cnf 3 4\n-3 -2 0 -2 1 0 2 3 0 -2 -1 0");
    compare(solver, -2, 0, 3, 0);

    parse("p cnf 4 4\n1 0 -1 2 0 -1 -2 3 0 -1 -2 -3 4 0");
    compare(solver, 1, 0, 2, 0, 3, 0, 4, 0);
  }

  /*
  // @Test
  public void pureLiterals1() throws Exception {
    parse("p cnf 3 2\n1 2 3 0 1 -2 -3 0");
    compare(solver.pureLiterals(), 1, 0);
  }

  // @Test
  public void pureLiterals2() throws Exception {
    parse("p cnf 4 5\n1 2 3 0 1 -2 -3 0 1 4 0 -4 -2 -3 0 2 3 4 0");
    compare(solver.pureLiterals(), 1, 0);
  }

  // @Test
  public void pureLiterals3() throws Exception {
    parse("p cnf 4 3\n-1 0 1 2 3 4 0 -2 -3 -4 0");
    compare(solver.pureLiterals());
  }

  // @Test
  public void pureLiterals4() throws Exception {
    parse("p cnf 3 2\n2 3 4 0 -2 -3 -4 0");
    compare(solver.pureLiterals());
  }

  // @Test
  public void pureLiterals5() throws Exception {
    parse("p cnf 3 3\n-1 0 2 3 4 0 2 -3 -4 0");
    compare(solver.pureLiterals(), 2, 0);
  }

  // @Test
  public void pureLiterals6() throws Exception {
    parse("p cnf 3 4\n-1 2 0 -1 -3 0 -2 3 4 0 -2 -3 -4 0");
    compare(solver.pureLiterals(), -1, 0);
  }

  // @Test
  public void pureLiterals7() throws Exception {
    parse("p cnf 5 5\n-1 2 0 -1 3 0 1 4 5 0 2 3 4 5 0 -2 -3 -4 -5 0");
    compare(solver.pureLiterals());

    parse("p cnf 5 5\n-1 2 0 -1 3 0 -1 4 5 0 2 3 4 5 0 -2 -3 -4 -5 0");
    compare(solver.pureLiterals(), -1, 0);
  }

  // @Test
  public void hyperBinaryResolution1() throws Exception {
    parse("p cnf 4 4\n1 2 0 1 3 0 1 4 0 -2 -3 -4 0");
    solver.hyperBinaryResolution();
    compare(solver.getAllUnits(), 1);

    parse("p cnf 5 5\n5 -1 0 1 2 0 1 3 0 1 4 0 -2 -3 -4 0");
    solver.hyperBinaryResolution();
    compare(solver.getAllUnits(), 5, 1);
  }

  // @Test
  public void hyperBinaryResolution2() throws Exception {
    parse("p cnf 4 3\n1 2 0 1 3 0 -2 -3 -4 0");
    solver.hyperBinaryResolution();
    assertTrue(solver.dag().containsEdge(-1, -4));
  }
  */

  private void parse(String text) throws Exception {
    Skeleton skeleton = Reader.parseText(text);
    solver = new Solver(skeleton, 0);
    while (solver.propagate()) { };
  }

  private static void compare(Solver solver, int... clauses) {
    Skeleton skeleton = solver.skeleton();
    skeleton.canonicalize();
    assertEquals(new TIntArrayList(clauses), skeleton.clauses);
  }

  private static void compare(TIntArrayList u, int... v) {
    assertEquals(new TIntArrayList(v), u);
  }
}
