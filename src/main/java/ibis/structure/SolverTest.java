package ibis.structure;

import org.apache.log4j.Logger;

import org.junit.Test;
import org.junit.Assert;


public final class SolverTest {
    private static final Logger logger = Logger.getLogger(SolverTest.class);

    private Solver load(String instance) {
        return new Solver(Skeleton.parse(instance));
    }

    private void check(Solver solver, String instance) {
        if (!instance.equals(solver.toString())) {
            logger.debug("Solver contains wrong instance!");
            logger.debug("solver   = " + solver);
            logger.debug("instance = " + instance);
        }
        Assert.assertTrue(instance.equals(solver.toString()));
    }

    private void checkToString(String begin, String end) {
        check(load(begin), end);
    }

    private void checkToString(String nice) {
        checkToString(nice, nice);
    }

    @Test
    public void toStringOneClause() {
        checkToString("-1");
        checkToString("(1 | -2)");
        checkToString("(-1 | 2 | -3)");
    }

    @Test
    public void toStringMultipleClauses() {
        checkToString("-1 & 1");
        checkToString("3 & (1 | -2)");
        checkToString("4 & (1 | 2) & (1 | 2 | 3)");
    }

    @Test
    public void toStringReorders() {
        checkToString("(1 | 2 | 3) & (-1 | 3 | 2)",
                      "(-1 | 2 | 3) & (1 | 2 | 3)");
    }

    @Test
    public void toStringIgnoresKnowns() {
        checkToString("1 & (1 | 2) & (2 | 3)", "1 & (2 | 3)");
        checkToString("1 & (-1 | 2) & (-1 | 2 | 3)", "1");
    }

    private void checkPropagate(String begin, String end, int... literals) {
        Solver solver = load(begin);
        for (int literal: literals) {
            solver.propagate(SAT.fromDimacs(literal));
            Assert.assertFalse(solver.isContradiction());
        }
        solver.check();
        check(solver, end);
    }

    @Test
    public void propagate() {
        checkPropagate("(1 | 2) & (-1 | 3)",
                       "1 & 3",
                       1);

        checkPropagate("(1 | 2 | 3) & (-1 | 3 | 2)",
                       "1 & (2 | 3)",
                       1);

        checkPropagate("(-1 | 2 | -2)",
                       "1",
                       1);

        checkPropagate("2 & (-1 | 2 | -3)",
                       "1 & 2",
                       1);

        checkPropagate("(-1 | 2 | -3) & (2 | 3)",
                       "1 & 2",
                       1);

        checkPropagate("(-1 | 2 | 3) & (2 | 3)",
                       "1 & (2 | 3)",
                       1);

        checkPropagate("(-1 | 2 | 2)",
                       "1 & 2",
                       1);

        checkPropagate("(1 | -2) & -2",
                       "-1 & -2",
                       -1);

        checkPropagate("(-1 | -2 | 3) & (1 | -2 | -3) & (1 | 2 | -3) & (1 | 2 | 3)",
                       "1 & (-2 | 3)",
                       1);

        checkPropagate("(-1 | -2 | 3) & (1 | -2 | -3) & (1 | 2 | -3) & (1 | 2 | 3)",
                       "-1 & -3 & 2",
                       -1);
    }

    @Test
    public void propagateChain() {
        checkPropagate("(-1 | 2) & (-2 | 3) & (-3 | 4) & (-4 | 5)",
                       "1 & 2 & 3 & 4 & 5",
                       1);

        checkPropagate("(-1 | 2) & (-1 | -2 | 3) & (-2 | -3 | 4) & (-3 | -4 | 5)",
                       "1 & 2 & 3 & 4 & 5",
                       1);
    }

    @Test
    public void propagateMultipleLiterals() {
        checkPropagate("(-1 | -2 | -3) & (3 | 4 | 5)",
                       "-3 & 1 & 2 & (4 | 5)",
                       1, 2);
    }

    private void checkUndo(String begin, int... literals) {
        Solver solver = load(begin);
        MapIntInt state = new MapIntInt();
        for (int literal: literals) {
            solver.propagate(literal, state, null);
            Assert.assertFalse(solver.isContradiction());
        }
        solver.undo(state);
        check(solver, begin);
    }

    @Test
    public void undo() {
        checkUndo("(-1 | 2)", 1);
        checkUndo("1", 1);
        checkUndo("(-1 | 2) & (-1 | -2 | -3)", -1);
        checkUndo("(2 | -2) & (-1 | -2 | -3)", 2);
    }

    /**
     * @todo This test is very simple because I have no plans
     * for double lookahead, yet.
     */
    @Test
    public void undoMultipleLiterals() {
        checkUndo("(-1 | 2) & (-1 | -2 | -3)", -1, 2);
    }

    private void checkSimplify(String begin, String end) {
        Solver solver = load(begin);
        solver.simplify();
        check(solver, end);
    }

    @Test
    public void simplifyPropagatesUnits() {
        checkSimplify("1 & -3 & 2", "-3 & 1 & 2");
        checkSimplify("1 & (-1 | -2)", "-2 & 1");
        checkSimplify("1 & (-1 | -2 | -3)", "1 & (-2 | -3)");
        checkSimplify("1 & (-1 | -2 | 2)", "1");
        checkSimplify("1 & 2 & (-1 | -2 | -3)", "-3 & 1 & 2");
        checkSimplify("1 & 2 & (-1 | -2 | -3) & (3 | 4 | 5)",
                      "-3 & 1 & 2 & (4 | 5)");
        checkSimplify("1 & 2 & (-1 | -3 | -4) & (-2 | -3 | -4)",
                      "1 & 2 & (-3 | -4)");
    }

    @Test
    public void simplifyRemovesBogusBinaries() {
        checkSimplify("(1 | 1)", "1");
        checkSimplify("(1 | -1)", "");
        checkSimplify("(1 | 2) & (1 | -2)", "1");
    }

    @Test
    public void simplifyRemovesBogusTernaries() {
        checkSimplify("(1 | 1 | 1)", "1");
        checkSimplify("(1 | 2 | -2)", "");
        checkSimplify("(1 | 2 | 2)", "(1 | 2)");
        checkSimplify("(1 | -1 | 2) & (3 | 4 | -3) & (5 | 6 | -6)", "");
        checkSimplify("(-1 | 1 | 1) & (2 | -2 | 2) & (3 | 3 | -3)", "");
        checkSimplify("(1 | 1 | 2) & (3 | 4 | 3) & (5 | 6 | 6)",
                      "(1 | 2) & (3 | 4) & (5 | 6)");
    }

    @Test
    public void simplifyComplex() {
        checkSimplify("1 & (-1 | 2)", "1 & 2");
        checkSimplify("1 & -2 & (-1 | 2 | 3)", "-2 & 1 & 3");
        checkSimplify("1 & (-1 | 2 | 3) & (2 | 2 | -3)", "1 & 2");
        checkSimplify("1 & -2 & (2 | -2 | -1)", "-2 & 1");
        checkSimplify("(2 | 2 | 1) & (-1 | -1 | 2)", "2");
        checkSimplify("(1 | 2 | 3) & (1 | 2 | 4)",
                      "(1 | 2 | 3) & (1 | 2 | 4)");
        checkSimplify("-1 & (1 | 2 | 4) & (1 | 2 | -4)",
                      "-1 & 2");
    }

    private boolean equals(SetInt set, int[] array) {
        if (set.size() != array.length)
            return false;

        SetInt tmp = new SetInt();
        for (int e: array) {
            if (tmp.has(e))
                return false;
            if (!set.has(e))
                return false;
            tmp.push(e);
        }

        return true;
    }

    private void checkLookahead(String begin, int variable,
                                int[] tUnits_, int[] fUnits_) {
        Solver solver = load(begin);
        SetInt tUnits = new SetInt();
        SetInt fUnits = new SetInt();

        for (int i = 0; i < tUnits_.length; ++i)
            tUnits_[i] = SAT.fromDimacs(tUnits_[i]);
        for (int i = 0; i < fUnits_.length; ++i)
            fUnits_[i] = SAT.fromDimacs(fUnits_[i]);

        boolean contradiction = solver.lookahead(variable, tUnits, fUnits);
        Assert.assertFalse(contradiction);

        Assert.assertTrue(equals(tUnits, tUnits_));
        Assert.assertTrue(equals(fUnits, fUnits_));
    }

    @Test
    public void lookahead() {
        checkLookahead("(1 | -2) & (-1 | 3)", 1,
                       new int[] { 1, 3 },
                       new int[] { -1, -2 });
        checkLookahead("(1 | -2) & (-1 | 3) & (2 | 4) & (-3 | 4)", 1,
                       new int[] { 1, 3, 4 },
                       new int[] { -1, -2, 4 });
    }

    private void checkLookaheadContradiction(String begin, int variable) {
        Solver solver = load(begin);
        boolean contradiction = solver.lookahead(variable, null, null);
        Assert.assertTrue(contradiction);
    }

    @Test
    public void lookaheadContradiction() {
        checkLookaheadContradiction("(1 | -2) & 2", 1);
        checkLookaheadContradiction("(-1 | -2) & 2", 1);
        checkLookaheadContradiction("(1 | -2) & 2 & (2 | 3 | 4)", 1);
        checkLookaheadContradiction("(-1 | -2) & 2 & (2 | 3 | 4)", 1);
        checkLookaheadContradiction("(1 | -2) & (-1 | -3) & 2 & 3", 1);
    }

    @Test
    public void constantPropagation() {
        SetInt tUnits = new SetInt();
        tUnits.push(2);
        tUnits.push(4);

        SetInt fUnits = new SetInt();
        fUnits.push(4);
        fUnits.push(6);

        Solver solver = load("(-2 | 3) & (-3 | 4 | 5) & (1 | 2 | 3)");
        solver.constantPropagation(tUnits, fUnits);
        check(solver, "2 & 3 & (4 | 5)");
    }
}
