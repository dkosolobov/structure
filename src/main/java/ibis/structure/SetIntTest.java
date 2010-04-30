package ibis.structure;

import org.junit.Test;
import org.junit.Assert;


public final class SetIntTest {
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
        for (int i = 0; i < (1 << 21); ++i)
            Assert.assertTrue(set.push(i) == i);
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
}

