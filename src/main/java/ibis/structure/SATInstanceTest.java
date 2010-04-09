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
        SATInstance instance = parse("(-2 | 4 | 7) & (2 | -4 | -7) & (-6)");
        Integer[] missing = new Integer[] { 1, 3, 5, 6 };

        int variable = instance.lookahead();
        Assert.assertTrue(1 <= variable && variable <= 7);
        Assert.assertTrue(Arrays.asList(missing).indexOf(variable) == -1);
    }

    @Test
    public void simple()
            throws Exception {
        SATInstance instance;
        int variable;
       
        instance = parse("(1)");
        variable = instance.lookahead();
        Assert.assertTrue(variable == 0);
        Assert.assertTrue(instance.isSatisfied());

        instance = parse("(1 | 2) & (-1 | -2)");
        variable = instance.lookahead();
        Assert.assertTrue(variable == 0);
        Assert.assertTrue(instance.isSatisfied());

        instance = parse("(-1) & (1)");
        variable = instance.lookahead();
        Assert.assertTrue(variable == 0);
        Assert.assertTrue(instance.isContradiction());

        instance = parse("(1) & (-1 | 2) & (-1 | 3) & (-2 | -3)");
        variable = instance.lookahead();
        Assert.assertTrue(variable == 0);
        Assert.assertTrue(instance.isContradiction());
    }
}
