package ibis.structure;

import org.junit.Test;
import org.junit.Assert;


public final class SetIntTest {
    private long timer;

    private void start() {
        timer = System.currentTimeMillis();
    }

    private void end(String message) {
        double elapsed = (System.currentTimeMillis() - timer) / 1000.;
        System.err.println(message + ": " + elapsed + "s");
    }

    @Test
    public void emptySet() {
        SetInt set = new SetInt();
        Assert.assertTrue(set.size() == 0);
        Assert.assertTrue(set.isEmpty());
        for (int i = 0; i < 16; ++i)
            Assert.assertFalse(set.has(i));
    }

    @Test
    public void correctSize() {
        SetInt set = new SetInt();
        for (int i = 0; i < 16; ++i) {
            Assert.assertTrue(set.size() == i);
            set.push(i);
        }
    }

    @Test
    public void pushAdds() {
        SetInt set = new SetInt();
        for (int i = 0; i < 16; ++i) {
            Assert.assertFalse(set.has(i));
            set.push(i);
            Assert.assertTrue(set.has(i));
        }
    }

    @Test
    public void popRemoves() {
        SetInt set = new SetInt();
        for (int i = 0; i < 16; ++i)
            set.insert(i);

        for (int i = 15; i >= 0; --i) {
            Assert.assertTrue(set.has(i));
            Assert.assertTrue(set.peekKey() == i);
            set.pop();
            Assert.assertFalse(set.has(i));
            Assert.assertTrue(set.size() == i);
        }
    }

    @Test
    public void peekReturnsLastKey() {
        SetInt set = new SetInt();
        for (int i = 0; i < 16; ++i) {
            set.insert(i);
            Assert.assertTrue(set.peekKey() == i);
        }
    }

    @Test
    public void consecutiveNumbers() {
        SetInt set = new SetInt();
        for (int i = 0; i < 16; ++i) {
            Assert.assertTrue(set.push(i) == i);
            Assert.assertTrue(set.has(i));
        }
        for (int i = 0; i < 16; ++i)
            Assert.assertTrue(set.has(i));
    }

    @Test
    public void sameElementInsert() {
        SetInt set = new SetInt();
        for (int i = 0; i < 16; ++i) {
            set.push(0);
            set.push(1);
        }

        Assert.assertTrue(set.has(0));
        Assert.assertTrue(set.has(1));
        Assert.assertTrue(set.size() == 2);
    }

    @Test
    public void doubleInserts() {
        SetInt set = new SetInt();
        for (int i = 0; i < 16; ++i)
            Assert.assertTrue(set.push(i) == i);
        for (int i = 0; i < 16; ++i)
            Assert.assertTrue(set.push(i) == i);
    }

    @Test
    public void manyRehashes() {
        SetInt set = new SetInt();
        for (int i = 0; i < (1 << 20); ++i)
            Assert.assertTrue(set.push(i) == i);
    }

    @Test
    public void manyPuts() {
        SetInt set;

        start();
        set = new SetInt();
        for (int i = 0; i < (1 << 21); ++i)
            set.push(i * i + i);
        end("put21");

        start();
        for (int i = 0; i < (1 << 21); ++i)
            set.has(i * i + i);
        end("has21");

        start();
        for (int i = 0; i < (1 << 21); ++i)
            set.pop();
        end("pop21");
    }

    @Test
    public void hasAllKeys() {
        SetInt set = new SetInt();
        for (int i = 0; i < 16; ++i)
            set.push(i);

        int[] keys = set.keys();
        Assert.assertTrue(keys.length == 16);
        for (int i = 0; i < 16; ++i)
            Assert.assertTrue(keys[i] == i);
    }

    @Test
    public void complex() {
        SetInt set = new SetInt();
        int[] es = new int[] { 3, 9, 16, 10, 20, 12, 4, 15 };
        for (int e: es)
            set.push(e);
        for (int e: es)
            Assert.assertTrue(set.has(e));
    }

}

