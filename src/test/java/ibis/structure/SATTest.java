package ibis.structure;

import org.junit.Test;
import org.junit.Assert;


public final class SATTest {
    @Test
    public void toDimacs() {
        Assert.assertTrue(SAT.toDimacs(2) == 1);
        Assert.assertTrue(SAT.toDimacs(3) == -1);
        Assert.assertTrue(SAT.toDimacs(6) == 3);
        Assert.assertTrue(SAT.toDimacs(7) == -3);
    }

    @Test
    public void fromDimacs() {
        Assert.assertTrue(SAT.fromDimacs(1) == 2);
        Assert.assertTrue(SAT.fromDimacs(-1) == 3);
        Assert.assertTrue(SAT.fromDimacs(3) == 6);
        Assert.assertTrue(SAT.fromDimacs(-3) == 7);
    }
}
