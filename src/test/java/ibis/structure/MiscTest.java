package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
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
    add(formula, XOR, 1, 2, 3);

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

  @Test
  public void iterator() {
    TIntArrayList formula = new TIntArrayList();
    ClauseIterator it;

    it = new ClauseIterator(formula);
    assertFalse(it.hasNext());

    add(formula, OR);
    it = new ClauseIterator(formula);
    assertTrue(it.hasNext());
    assertEquals(it.next(), 1);
    assertFalse(it.hasNext());

    add(formula, XOR, 1, 2, 3);
    it = new ClauseIterator(formula);
    assertTrue(it.hasNext());
    assertEquals(it.next(), 1);
    assertTrue(it.hasNext());
    assertEquals(it.next(), 2);
    assertFalse(it.hasNext());

    add(formula, NXOR, 3, -3);
    it = new ClauseIterator(formula);
    assertTrue(it.hasNext());
    assertEquals(it.next(), 1);
    assertTrue(it.hasNext());
    assertEquals(it.next(), 2);
    assertTrue(it.hasNext());
    assertEquals(it.next(), 6);
    assertFalse(it.hasNext());

    removeClause(formula, 2);
    it = new ClauseIterator(formula);
    assertTrue(it.hasNext());
    assertEquals(it.next(), 1);
    assertTrue(it.hasNext());
    assertEquals(it.next(), 6);
    assertFalse(it.hasNext());

    removeClause(formula, 1);
    it = new ClauseIterator(formula);
    assertTrue(it.hasNext());
    assertEquals(it.next(), 6);
    assertFalse(it.hasNext());

    removeClause(formula, 6);
    it = new ClauseIterator(formula);
    assertFalse(it.hasNext());
  }

  private void add(final TIntArrayList formula,
                   final int type,
                   final int... clause) {
    formula.add(encode(clause.length, type));
    formula.add(clause);
  }
}
