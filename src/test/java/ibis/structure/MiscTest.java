package ibis.structure;

import gnu.trove.TIntArrayList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import static ibis.structure.Misc.*;


public class MiscTest {
  @Test
  public void literals() {
    int u = 7;

    assertEquals(neg(neg(u)), u);
    assertEquals(neg(neg(neg(u))), neg(u));
    assertEquals(var(neg(u)), var(u));
  }

  @Test
  public void clause() {
    TIntArrayList formula = new TIntArrayList();
    formula.add(encode(3, XOR));
    formula.add(1);
    formula.add(2);
    formula.add(3);

    assertEquals(length(formula, 1), 3);
    assertEquals(type(formula, 1), XOR);

    switchXOR(formula, 1);
    assertEquals(type(formula, 1), NXOR);
    switchXOR(formula, 1);
    assertEquals(type(formula, 1), XOR);

    removeLiteral(formula, 1, 2);
    assertEquals(length(formula, 1), 2);
    assertEquals(type(formula, 1), XOR);

    removeClause(formula, 1);
    assertTrue(isClauseRemoved(formula, 1));

    compact(formula);
    assertTrue(formula.isEmpty());
  }
}
