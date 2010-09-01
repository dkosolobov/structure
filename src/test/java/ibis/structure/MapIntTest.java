package ibis.structure;

import org.junit.Test;
import org.junit.Assert;

public final class MapIntTest {
    @Test
    public void simpleGet() {
        MapInt<Object> map = new MapInt<Object>();
        Object[] os = new Object[16];

        for (int i = 101; i <= 116; ++i) {
            os[i - 101] = new Object();
            map.put(i, os[i - 101]);
            Assert.assertTrue(map.get(i) == os[i - 101]);
        }

        for (int i = 101; i <= 116; ++i)
            Assert.assertTrue(map.get(i) == os[i - 101]);
    }

    @Test
    public void getWithDefault() {
        MapInt<Object> map = new MapInt<Object>();
        Object default_ = new Object();

        for (int i = 101; i <= 116; ++i) {
            Object o = new Object();
            Assert.assertTrue(map.get(i, default_) == default_);
            map.put(i, o);
            Assert.assertTrue(map.get(i, default_) == o);
        }
    }

    @Test
    public void doublePutReplaces() {
        MapInt<Object> map = new MapInt<Object>();

        for (int i = 101; i <= 116; ++i)
            map.put(i, new Object());

        for (int i = 101; i <= 116; ++i) {
            Object o = new Object();
            map.put(i, o);
            Assert.assertTrue(map.get(i) == o);
        }
    }
}
