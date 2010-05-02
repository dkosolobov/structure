package ibis.structure;

import org.junit.Test;
import org.junit.Assert;

public final class SkeletonTest {
    @Test
    public void parseFindsUnaries() {
        String nice = "1 & -3 & -1 & 1";
        Skeleton skeleton = Skeleton.parse(nice);

        Assert.assertTrue(skeleton.numVariables == 3);
        Assert.assertTrue(skeleton.clauses[1][0].length == 4);
        Assert.assertTrue(skeleton.clauses[1][0][0] == 2);
        Assert.assertTrue(skeleton.clauses[1][0][1] == 7);
        Assert.assertTrue(skeleton.clauses[1][0][2] == 3);
        Assert.assertTrue(skeleton.clauses[1][0][3] == 2);
    }

    @Test
    public void parseFindsBinaries() {
        String nice = "(1 | 2) & (-1 | -4)";
        Skeleton skeleton = Skeleton.parse(nice);
        Assert.assertTrue(skeleton.numVariables == 4);

        Assert.assertTrue(skeleton.clauses[2][0].length == 2);
        Assert.assertTrue(skeleton.clauses[2][0][0] == 2);
        Assert.assertTrue(skeleton.clauses[2][1][0] == 4);
        Assert.assertTrue(skeleton.clauses[2][0][1] == 3);
        Assert.assertTrue(skeleton.clauses[2][1][1] == 9);
    }

    @Test
    public void parseFindsTernaries() {
        String nice = "(4 | -3 | -1) & (1 | 4 | 5)";
        Skeleton skeleton = Skeleton.parse(nice);
        Assert.assertTrue(skeleton.numVariables == 5);

        Assert.assertTrue(skeleton.clauses[3][0].length == 2);
        Assert.assertTrue(skeleton.clauses[3][0][0] == 8);
        Assert.assertTrue(skeleton.clauses[3][1][0] == 7);
        Assert.assertTrue(skeleton.clauses[3][2][0] == 3);
        Assert.assertTrue(skeleton.clauses[3][0][1] == 2);
        Assert.assertTrue(skeleton.clauses[3][1][1] == 8);
        Assert.assertTrue(skeleton.clauses[3][2][1] == 10);
    }

    @Test
    public void complexFormula1() {
        String nice = "1 & -3 & -1 & 1 & (1 | 2) & (-1 | -4) & (4 | -3 | -1) & (1 | 4 | 5)";
        Skeleton skeleton = Skeleton.parse(nice);
        Assert.assertTrue(skeleton.numVariables == 5);

        Assert.assertTrue(skeleton.clauses[1][0].length == 4);
        Assert.assertTrue(skeleton.clauses[1][0][0] == 2);
        Assert.assertTrue(skeleton.clauses[1][0][1] == 7);
        Assert.assertTrue(skeleton.clauses[1][0][2] == 3);
        Assert.assertTrue(skeleton.clauses[1][0][3] == 2);

        Assert.assertTrue(skeleton.clauses[2][0].length == 2);
        Assert.assertTrue(skeleton.clauses[2][0][0] == 2);
        Assert.assertTrue(skeleton.clauses[2][1][0] == 4);
        Assert.assertTrue(skeleton.clauses[2][0][1] == 3);
        Assert.assertTrue(skeleton.clauses[2][1][1] == 9);

        Assert.assertTrue(skeleton.clauses[3][0].length == 2);
        Assert.assertTrue(skeleton.clauses[3][0][0] == 8);
        Assert.assertTrue(skeleton.clauses[3][1][0] == 7);
        Assert.assertTrue(skeleton.clauses[3][2][0] == 3);
        Assert.assertTrue(skeleton.clauses[3][0][1] == 2);
        Assert.assertTrue(skeleton.clauses[3][1][1] == 8);
        Assert.assertTrue(skeleton.clauses[3][2][1] == 10);
    }

    @Test
    public void complexFormula2() {
        String nice = "(1 | 2 | 3) & (-1 | 3 | 2)";
        Skeleton skeleton = Skeleton.parse(nice);
        Assert.assertTrue(skeleton.numVariables == 3);

        Assert.assertTrue(skeleton.clauses[3][0].length == 2);
        Assert.assertTrue(skeleton.clauses[3][0][0] == 2);
        Assert.assertTrue(skeleton.clauses[3][1][0] == 4);
        Assert.assertTrue(skeleton.clauses[3][2][0] == 6);
        Assert.assertTrue(skeleton.clauses[3][0][1] == 3);
        Assert.assertTrue(skeleton.clauses[3][1][1] == 6);
        Assert.assertTrue(skeleton.clauses[3][2][1] == 4);
    }

}
