package ibis.structure;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SkeletonTest {
  private Skeleton skeleton;

  @Test
  public void units() throws Exception {
    parse("p cnf 3 3\n1 0 2 0 -3 0");
    compare(0);
    compare(1, 1, 2, -3);
    compare(2);
  }

  @Test
  public void binaries() throws Exception {
    parse("p cnf 3 3\n1 -1 0 2 3 0 4 5 0");
    compare(0);
    compare(1);
    compare(2, 1, -1, 2, 3, 4, 5);
  }

  @Test
  public void clauses() throws Exception {
    parse("p cnf 3 3\n1 2 3 0 -1 -2 -3 0 -1 -2 3 -3 0");
    compare(0, 1, 2, 3, 0, -1, -2, -3, 0, -1, -2, 3, -3, 0);
    compare(1);
    compare(2);
  }

  @Test
  public void instance() throws Exception {
    parse("p cnf 4 5\n1 2 3 0 -1 2 -3 0 2 -3 4 0 1 0 -3 -4 0");
    compare(0, 1, 2, 3, 0, -1, 2, -3, 0, 2, -3, 4, 0);
    compare(1, 1);
    compare(2, -3, -4);
    assertEquals(1, skeleton.numUnits());
  }

  @Test
  public void canonicalize1() throws Exception {
    parse("p cnf 1 4\n1 0 1 -1 0 1 1 0 1 -1 -1 0");
    skeleton.canonicalize();
    compare(0);
    compare(1, 1);
    compare(2);
  }

  @Test
  public void canonicalize2() throws Exception {
    parse("p cnf 3 3\n1 -1 2 0 1 1 1 0 -2 -2 -3 0");
    skeleton.canonicalize();
    compare(0);
    compare(1, 1);
    compare(2, -2, -3);
  }

  @Test
  public void canonicalizeBinaries() throws Exception {
    parse("p cnf 4 5\n1 2 0 2 2 0 3 0 -4 -3 0 4 3 0");
    skeleton.canonicalize();
    compare(0);
    compare(1, 2, 3, -4);
    compare(2, 1, 2);
  }

  @Test(expected=ContradictionException.class)
  public void contradiction1() throws Exception {
    parse("p cnf 1 2\n1 0 -1 0");
    skeleton.canonicalize();
  }

  @Test(expected=ContradictionException.class)
  public void contradiction2() throws Exception {
    parse("p cnf 2 3\n1 0 2 0 -1 -2 0");
    skeleton.canonicalize();
  }

  @Test(expected=ContradictionException.class)
  public void contradiction3() throws Exception {
    parse("p cnf 3 4\n1 0 2 0 3 0 -1 -2 -3 0");
    skeleton.canonicalize();
  }

  @Test(expected=ContradictionException.class)
  public void contradiction4() throws Exception {
    parse("p cnf 3 4\n1 0 2 0 -1 -2 -3 0 3 0");
    skeleton.canonicalize();
  }

  @Test(expected=ContradictionException.class)
  public void contradiction5() throws Exception {
    parse("p cnf 1 2\n-1 0 1 1 0");
    skeleton.canonicalize();
  }

  private void parse(String text) throws Exception {
    skeleton = Reader.parseText(text);
  }

  private void compare(int index, int... clause) {
    if (index == 1) {
      assertEquals(new TIntHashSet(clause),
                   new TIntHashSet(skeleton.clauses[index].toNativeArray()));
    } else {
      assertEquals(new TIntArrayList(clause), skeleton.clauses[index]);
    }
  }
}
