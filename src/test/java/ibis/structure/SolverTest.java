package ibis.structure;

import org.junit.Test;
import gnu.trove.TIntArrayList;
import static org.junit.Assert.assertEquals;

public class SolverTest {
  private Solver solver;

  @Test(expected=ContradictionException.class)
  public void contradiction1() throws Exception {
    parse("p cnf 1 2\n1 0 -1 0");
    solver.simplify();
  }

  @Test(expected=ContradictionException.class)
  public void contradiction2() throws Exception {
    parse("p cnf 2 3\n1 0 2 0 -1 -2 0");
    solver.simplify();
  }

  @Test(expected=ContradictionException.class)
  public void contradiction3() throws Exception {
    parse("p cnf 3 4\n1 0 2 0 3 0 -1 -2 -3 0");
    solver.simplify();
  }

  @Test(expected=ContradictionException.class)
  public void contradiction4() throws Exception {
    parse("p cnf 3 4\n1 0 2 0 -1 -2 -3 0 3 0");
    solver.simplify();
  }

  @Test(expected=ContradictionException.class)
  public void contradiction5() throws Exception {
    parse("p cnf 1 2\n-1 0 1 1 0");
    solver.simplify();
  }

  @Test
  public void simplify1() throws Exception {
    parse("p cnf 1 4\n1 0 1 -1 0 1 1 0 1 -1 -1 0");
    solver.simplify();
    compare(1, 0);
  }

  @Test
  public void simplify2() throws Exception {
    parse("p cnf 3 3\n1 -1 2 0 1 1 1 0 -2 -2 -3 0");
    solver.simplify();
    compare(1, 0, -2, -3, 0);
  }

  @Test
  public void simplify3() throws Exception {
    parse("p cnf 3 4\n-3 -2 0 -2 1 0 2 3 0 -2 -1 0");
    solver.simplify();
    compare(-2, 0, 3, 0);
  }

  private void parse(String text) throws Exception {
    Skeleton skeleton = Reader.parseText(text);
    solver = new Solver(skeleton);
  }

  private void compare(int... clauses) {
    Skeleton skeleton = solver.skeleton();
    skeleton.canonicalize();
    assertEquals(new TIntArrayList(clauses),
                 skeleton.clauses);
  }
}
