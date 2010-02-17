package ibis.structure;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.minisat.core.Solver;
import org.sat4j.reader.InstanceReader;
import org.sat4j.reader.ParseFormatException;
import org.sat4j.reader.Reader;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IProblem;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.IVecInt;

import ibis.cohort.ActivityIdentifier;
import ibis.cohort.Activity;
import ibis.cohort.Event;
import ibis.cohort.Context;


public class CohortJob extends Activity {
    private static final long serialVersionUID = 1L;

    private static StructureLogger logger = StructureLogger.getLogger(CohortJob.class);

    private ActivityIdentifier parent;
    private String problemName;
    private IVecInt assumps;

    public CohortJob(ActivityIdentifier parent, String problemName, IVecInt assumps) {
        super(Context.ANY);
        this.parent = parent;
        this.problemName = problemName;
        this.assumps = assumps;
    }

    @Override
    public void initialize() throws Exception {
        logger.debug("running " + toString());

        IProblem problem = null;
        try { problem = readProblem(problemName); }
        catch (Exception e) { e.printStackTrace(); }

        int[] model = problem.findModel(assumps);
        cohort.send(identifier(), parent, model);
        finish();
    }

    @Override public void process(Event arg0) throws Exception {
    }

    @Override
    public void cleanup() throws Exception {
    }

    @Override
    public void cancel() throws Exception {
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("COHORT identifier " + identifier() + ", job:");
        for (int i = 0; i < assumps.size(); ++i)
            buffer.append(" " + assumps.get(i));
        return buffer.toString();
    }

    public static IProblem readProblem(String problemName)
            throws FileNotFoundException, ParseFormatException, IOException, ContradictionException {
        SolverFactory factory = SolverFactory.instance();
        Solver<?> solver = (Solver<?>)factory.defaultSolver();
        Reader reader = new InstanceReader(solver);
        IProblem problem = reader.parseInstance(problemName);
        return problem;
    }
}

