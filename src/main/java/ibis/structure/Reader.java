package ibis.structure;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Vector;

import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IConstr;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.IVec;
import org.sat4j.specs.IVecInt;
import org.sat4j.specs.SearchListener;
import org.sat4j.specs.TimeoutException;


/**
 * Reader is used to read instances using the SAT4J reader.
 */
public final class Reader implements ISolver {
    private int numVariables;
    private int numConstraints;
    private VecInt[][] clauses;

    public Reader() {
        reset();
    }

    public Skeleton skeleton() {
        Skeleton skeleton = new Skeleton();
        skeleton.numVariables = numVariables;

        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < i; ++j)
                skeleton.clauses[i][j] = clauses[i][j].toArray();

        return skeleton;
    }

    /*** ISolver methods ***/

    @Deprecated
	public int newVar() {
        throw new UnsupportedOperationException();
    }

    public int newVar(int howMany) {
        if (numVariables != 0)
            throw new UnsupportedOperationException(
                    "Only one call to newVar(...) allowed");
        numVariables = howMany;
        return numVariables;
    }

	public int nextFreeVarId(boolean reserve) {
        throw new UnsupportedOperationException();
    }

    public void setExpectedNumberOfClauses(int numClauses) {
    }

    private int extra(int var, boolean negated) {
        return (numVariables - 1 + var) * 2 + (negated ? 1 : 0);
    }

    private void addUnit(int first)
            throws ContradictionException {
        ++numConstraints;
        clauses[1][0].push(first);
    }

    private void addBinary(int first, int second)
            throws ContradictionException {
        ++numConstraints;
        clauses[2][0].push(first);
        clauses[2][1].push(second);
    }

    private void addTernary(int first, int second, int third)
            throws ContradictionException {
        ++numConstraints;
        clauses[3][0].push(first);
        clauses[3][1].push(second);
        clauses[3][2].push(third);
    }

	public IConstr addClause(IVecInt literals)
            throws ContradictionException {
        if (literals.size() == 0)
            return null;

        // unit
        int first = SAT.fromDimacs(literals.get(0));
        if (literals.size() == 1) {
            addUnit(first);
            return null;
        }

        // binary
        int second = SAT.fromDimacs(literals.get(1));
        if (literals.size() == 2) {
            addBinary(first, second);
            return null;
        }

        // ternary
        int third = SAT.fromDimacs(literals.get(2));
        if (literals.size() == 3) {
            addTernary(first, second, third);
            return null;
        }

        // longer, must transform
        int k = literals.size();
        for (int i = 2; i <= k - 3; ++i) {
            int first_ = extra(i - 1, false);
            int second_ = SAT.fromDimacs(literals.get(i));
            int third_ = extra(i, true);
            addTernary(first_, second_, third_);
        }
        addTernary(
                SAT.fromDimacs(literals.get(0)),
                SAT.fromDimacs(literals.get(1)),
                extra(1, false));
        addTernary(
                extra(k - 3, true),
                SAT.fromDimacs(literals.get(k - 2)),
                SAT.fromDimacs(literals.get(k - 1)));
        numVariables += k - 3;

        return null;  /* intended because removeConstr(...) is not implemented */
    }

    public IConstr addBlockingClause(IVecInt literals)
            throws ContradictionException {
        throw new UnsupportedOperationException();
    }

    public boolean removeConstr(IConstr c) {
        throw new UnsupportedOperationException();
    }

	public boolean removeSubsumedConstr(IConstr c) {
        throw new UnsupportedOperationException();
    }

	public void addAllClauses(IVec<IVecInt> clauses)
            throws ContradictionException {
        throw new UnsupportedOperationException();
    }

	public IConstr addAtMost(IVecInt literals, int degree)
			throws ContradictionException {
        throw new UnsupportedOperationException();
    }

	public IConstr addAtLeast(IVecInt literals, int degree)
			throws ContradictionException {
        throw new UnsupportedOperationException();
    }

	public void setTimeout(int t) {
        throw new UnsupportedOperationException();
    }

	public void setTimeoutOnConflicts(int count) {
        throw new UnsupportedOperationException();
    }

	public void setTimeoutMs(long t) {
        throw new UnsupportedOperationException();
    }

	public int getTimeout() {
        throw new UnsupportedOperationException();
    }

	public long getTimeoutMs() {
        throw new UnsupportedOperationException();
    }

	public void expireTimeout() {
        throw new UnsupportedOperationException();
    }

	public void reset() {
        numVariables = 0;
        numConstraints = 0;

        clauses = new VecInt[4][];
        for (int i = 0; i < 4; ++i) {
            clauses[i] = new VecInt[i];
            for (int j = 0; j < i; ++j)
                clauses[i][j] = new VecInt();
        }
    }

	@Deprecated
	public void printStat(PrintStream out, String prefix) {
        throw new UnsupportedOperationException();
    }

	public void printStat(PrintWriter out, String prefix) {
        throw new UnsupportedOperationException();
    }

	public Map<String, Number> getStat() {
        throw new UnsupportedOperationException();
    }

	public String toString(String prefix) {
        throw new UnsupportedOperationException();
    }

	public void clearLearntClauses() {
        throw new UnsupportedOperationException();
    }

	public void setDBSimplificationAllowed(boolean status) {
        throw new UnsupportedOperationException();
    }

	public boolean isDBSimplificationAllowed() {
        throw new UnsupportedOperationException();
    }

	public void setSearchListener(SearchListener sl) {
        throw new UnsupportedOperationException();
    }

	public SearchListener getSearchListener() {
        throw new UnsupportedOperationException();
    }

	public boolean isVerbose() {
        throw new UnsupportedOperationException();
    }

	public void setVerbose(boolean value) {
        throw new UnsupportedOperationException();
    }

	public void setLogPrefix(String prefix) {
        throw new UnsupportedOperationException();
    }

	public String getLogPrefix() {
        throw new UnsupportedOperationException();
    }


    /*** IProblem methods ***/
    public int[] model() {
        throw new UnsupportedOperationException();
    }

    public boolean model(int var) {
        throw new UnsupportedOperationException();
    }

    public boolean isSatisfiable()
            throws TimeoutException {
        throw new UnsupportedOperationException();
    }

    public boolean isSatisfiable(IVecInt assumps, boolean globalTimeout)
            throws TimeoutException {
        throw new UnsupportedOperationException();
    }

    public boolean isSatisfiable(boolean globalTimeout)
            throws TimeoutException {
        throw new UnsupportedOperationException();
    }

    public boolean isSatisfiable(IVecInt assumps)
            throws TimeoutException {
        throw new UnsupportedOperationException();
    }

    public int[] findModel()
            throws TimeoutException {
        throw new UnsupportedOperationException();
    }

    public int[] findModel(IVecInt assumps)
            throws TimeoutException {
        throw new UnsupportedOperationException();
    }

    public int nConstraints() {
        return numConstraints;
    }

    public int nVars() {
        return numVariables;
    }

    public void printInfos(PrintWriter out, String prefix) {
        throw new UnsupportedOperationException();
    }
}


