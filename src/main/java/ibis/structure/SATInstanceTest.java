package ibis.structure;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import org.sat4j.core.VecInt;
import org.sat4j.specs.ContradictionException;


public final class SATInstanceTest {
    private SATInstance instance;

    private static final VecInt clause(int... literals) {
        VecInt clause = new VecInt();
        for (int l: literals)
            clause.push(l);
        return clause;
    }
    
    @Before
    public void setUp()
            throws Exception {
        instance = new SATInstance();

        instance.newVar(10);
        instance.setExpectedNumberOfClauses(3); 

        instance.addClause(clause(1));
        instance.addClause(clause(2, -3, 1));
    }
    
    @Test
    public void toDimacs() {
        Assert.assertTrue(SATInstance.toDimacs(2) == 1);
        Assert.assertTrue(SATInstance.toDimacs(3) == -1);
        Assert.assertTrue(SATInstance.toDimacs(6) == 3);
        Assert.assertTrue(SATInstance.toDimacs(7) == -3);
    }

    @Test
    public void fromDimacs() {
        Assert.assertTrue(SATInstance.fromDimacs(1) == 2);
        Assert.assertTrue(SATInstance.fromDimacs(-1) == 3);
        Assert.assertTrue(SATInstance.fromDimacs(3) == 6);
        Assert.assertTrue(SATInstance.fromDimacs(-3) == 7);
    }
}
