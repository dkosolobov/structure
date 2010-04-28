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
 * Reader.
 *
 * This class is used to read a SAT instance using
 * the sat4j reader. The instance can be converted to
 * a skeleton which can be passed to a solver.
 * The SAT formula is transformed into a 3-SAT formula
 * (ie. all clauses have at most 3 literals). 
 * All trivial clauses (eg. x | !x) are reduced or eliminated.
 * Duplicate clauses are eliminated.
 * Trivial contradictions like x & !x are also detected.
 */
public final class Reader implements ISolver {
    private int numVariables;
    private SetInt units;
    private MapInt<SetInt> binaries;
    private MapInt<MapInt<SetInt>> ternaries;

    public Reader() {
        reset();
    }

    private void backtrackHelper(
            int depth, int maxDepth, int[] path,
            HashInt tree, Vector<Integer>[] store) {
        int[] keys = tree.keys();
        for (int k: keys) {
            path[depth] = k;

            if (depth < maxDepth - 1) {
                backtrackHelper(
                        depth + 1, maxDepth, path,
                        (HashInt)(((MapInt)tree).get(k)), store);
            } else {
                for (int d = 0; d < maxDepth; ++d)
                    store[d].add(path[d]);
            }
        }
    }

    private void backtrack(int maxDepth, HashInt tree, Vector<Integer>[] store) {
        backtrackHelper(0, maxDepth, new int[maxDepth], tree, store);
    }

    private int[][] convert(Vector<Integer>[] store) {
        int[][] store_ = new int[store.length][];
        for (int i = 0; i < store.length; ++i) {
            store_[i] = new int[store[i].size()];
            for (int j = 0; j < store[i].size(); ++j)
                store_[i][j] = store[i].elementAt(j);
        }
        return store_;
    }

    public Skeleton skeleton() {
        Skeleton skeleton = new Skeleton();
        skeleton.numVariables = numVariables;

        Vector<Integer>[] units_ = new Vector[1];
        units_[0] = new Vector<Integer>();
        backtrack(1, units, units_);
        skeleton.units = convert(units_);

        Vector<Integer>[] binaries_ = new Vector[2];
        binaries_[0] = new Vector<Integer>();
        binaries_[1] = new Vector<Integer>();
        backtrack(2, binaries, binaries_);
        skeleton.binaries = convert(binaries_);

        Vector<Integer>[] ternaries_ = new Vector[3];
        ternaries_[0] = new Vector<Integer>();
        ternaries_[1] = new Vector<Integer>();
        ternaries_[2] = new Vector<Integer>();
        backtrack(3, ternaries, ternaries_);
        skeleton.ternaries = convert(ternaries_);

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
        if (units.has(first ^ 1))
            throw new ContradictionException();
        units.push(first);
    }

    private void addBinary(int first, int second)
            throws ContradictionException {
        /* sorts first, second */
        int temp;
        if (first > second) { temp = first; first = second; second = temp; }

        /* eliminates trivial cases */
        if (first == second) {
            /* first | first */
            addUnit(first);
            return;
        }
        if ((first ^ 1) == second) {
            /* first | !first */
            return;
        }

        /* first | second */
        binaries.
                get(first, new SetInt()).push(second);
    }

    private void addTernary(int first, int second, int third)
            throws ContradictionException {
        /* sorts first, second, third */
        int temp;
        if (first > second) { temp = first; first = second; second = temp; }
        if (second > third) { temp = second; second = third; third = temp; }
        if (first > second) { temp = first; first = second; second = temp; }
        assert first <= second && second <= third;

        /* eliminates trivial cases when a literal appeares twice */
        if (first == second) {
            /* first | first | third */
            addBinary(first, third);
            return;
        }
        if (second == third) {
            /* first | second | second */
            addBinary(first, second);
            return;
        }
        assert first < second && second < third;

        /* eliminates trivial cases when a variable appeares twice */
        if (first == (second ^ 1)) {
            /* first | !first | third */
            addUnit(third);
            return;
        }
        if (second == (third ^ 1)) {
            /* first | second | !second */
            addUnit(first);
            return;
        }

        ternaries.
                get(first, new MapInt<SetInt>()).
                get(second, new SetInt()).push(second);
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
        units = new SetInt();
        binaries = new MapInt<SetInt>();
        ternaries = new MapInt<MapInt<SetInt>>();
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
        throw new UnsupportedOperationException();
    }

    public int nVars() {
        return numVariables;
    }

    public void printInfos(PrintWriter out, String prefix) {
        throw new UnsupportedOperationException();
    }
}


