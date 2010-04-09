package ibis.structure;

import java.util.Vector;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import org.sat4j.core.VecInt;
import org.sat4j.specs.ContradictionException;


public final class SATInstanceTest {
    private static final VecInt clause(int... literals) {
        VecInt clause = new VecInt();
        for (int l: literals)
            clause.push(l);
        return clause;
    }

    private static final SATInstance parse(String nice)
            throws ContradictionException {
        // divides in clauses
        String[] clauses = nice.split(" [&] ");
        Vector<VecInt> instance = new Vector<VecInt>();
        int numVariables = 0;

        for (String clause: clauses) {
            // cuts parantheses
            clause = clause.substring(1, clause.length() - 1);
            String[] variables = clause.split(" [|] ");
            VecInt clause_ = new VecInt();

            // trasnforms the clause from String to VecInt
            // as required by SATInstance
            for (String variable: variables) {
                System.err.println("variable = " + variable);
                int variable_ = Integer.parseInt(variable);
                numVariables = Math.max(numVariables, Math.abs(variable_));
                clause_.push(variable_);
            }

            instance.add(clause_);
        }

        SATInstance instance_ = new SATInstance();
        instance_.newVar(numVariables);
        instance_.setExpectedNumberOfClauses(instance.size());
        for (VecInt clause: instance)
            instance_.addClause(clause);
        return instance_;
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

    @Test
    public void missingVariables()
            throws Exception {
        SATInstance instance = parse(
                "(-16 | 13 | 19) & (-13 | 15) & (-19 | -15 | 17) & " +
                "(-16 | 17) & (13 | 15) & (-17 | 19) & " +
                "(-17 | 13 | 16 | 19) & (-16 | 15 | 19)");
        Integer[] missing = new Integer[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 18
            };

        int variable = instance.lookahead();
        Assert.assertTrue(1 <= variable && variable <= 19);
        Assert.assertTrue(Arrays.asList(missing).indexOf(variable) == -1);
    }

    @Test
    public void solvedVariables()
            throws Exception {
        SATInstance instance = parse(
                "(-16 | 13 | 19) & (-13 | 15) & (-19 | -15 | 17) & " +
                "(-16 | 17) & (13 | 15) & (-17 | 19) & " +
                "(-17 | 13 | 16 | 19) & (-16 | 15 | 19)");
        Integer[] solved = new Integer[] {
                13, 15, 16
            };

        int variable = instance.lookahead();
        Assert.assertTrue(1 <= variable && variable <= 19);
        Assert.assertTrue(Arrays.asList(solved).indexOf(variable) == -1);
    }
}
