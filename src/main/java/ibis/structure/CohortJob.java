package ibis.structure;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.minisat.core.Solver;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IProblem;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.IVecInt;

import ibis.cohort.ActivityIdentifier;
import ibis.cohort.Activity;
import ibis.cohort.MessageEvent;
import ibis.cohort.Event;
import ibis.cohort.Context;


public class CohortJob extends Activity {
    private static final long serialVersionUID = 1L;
    private static final StructureLogger logger = StructureLogger.getLogger(CohortJob.class);

    private final ActivityIdentifier counter;
    private final ActivityIdentifier listener;
    private final SATInstance instance;
    private final int decision;

    public CohortJob(
            ActivityIdentifier counter, ActivityIdentifier listener,
            SATInstance instance, int decision) {
        super(Context.ANY);

        this.counter = counter;
        this.listener = listener;
        this.instance = instance;
        this.decision = decision;
    }

    public void initialize()
            throws Exception {
        /* makes the decision first */
        if (decision != 0) {
            // logger.debug("decision " + SATInstance.toDimacs(decision) + " on " +
                         // "instance " + instance);
            boolean contradiction = instance.propagate(decision, null);
            if (contradiction) {
                /* TODO: this is an error an should be treated as such */
                logger.error("Propagation of " + SATInstance.toDimacs(decision) +
                             " returned in contradiction");
                System.exit(1);
            }
        }

        // logger.debug("solving formula " + instance);
        int lookahead = instance.lookahead();

        if (lookahead == 0) {
            if (instance.isSatisfied()) {
                logger.debug("instance is satisfiable");
                cohort.send(identifier(), listener, instance.model());
            } else if (instance.isContradiction()) {
                logger.debug("instance is a contradiction");
            } else {
                logger.error(
                        "Instance " + this + " was not satisfied and is not a " +
                        "contradiction, but lookahead didn't find any " +
                        "variable to branch on.");
            }
        } else {
            // logger.debug("branching on " + lookahead + " for " + instance);
            cohort.send(identifier(), counter, 2);

            /* FIXME: I assumed that a copy of instance is already done */
            cohort.submit(new CohortJob(
                    counter, listener, instance.deepCopy(), lookahead * 2 + 0));
            cohort.submit(new CohortJob(
                    counter, listener, instance.deepCopy(), lookahead * 2 + 1));
        }

        /* job finished, decrease counter */
        finish();
        cohort.send(identifier(), counter, -1);
    }

    public void process(Event arg0)
            throws Exception {
    }

    public void cleanup()
            throws Exception {
    }

    public void cancel()
            throws Exception {
    }

    public String toString() {
        return "CohortJob: instance = " + instance +
               (decision == 0 ? "" : ", decision = " + SATInstance.toDimacs(decision));
    }
}

