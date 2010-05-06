package ibis.structure;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import ibis.cohort.ActivityIdentifier;
import ibis.cohort.Activity;
import ibis.cohort.MessageEvent;
import ibis.cohort.Event;
import ibis.cohort.Context;
import ibis.cohort.SingleEventCollector;

import org.apache.log4j.Logger;


public class CohortJob extends Activity {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(CohortJob.class);

    private static ES es = new ES();

    private final ActivityIdentifier counter;
    private final ActivityIdentifier listener;
    private final Skeleton skeleton;
    private final int decision;

    private ES.Gene gene;
    private Solver solver;
    private int lookahead;

    public CohortJob(
            ActivityIdentifier counter, ActivityIdentifier listener,
            Skeleton skeleton, int decision) {
        super(Context.ANY);

        this.counter = counter;
        this.listener = listener;
        this.skeleton = skeleton;
        this.decision = decision;
    }

    private static int count = 0;

    private synchronized void printStats(Skeleton skeleton, ES.Gene gene) {
        ++count;
        if (count % 8 == 0) {
            logger.debug(skeleton.numVariables() + " variables, " +
                         skeleton.numConstraints() + " constraints, " +
                         skeleton.numUnits() + " solved, " +
                         "gene is " + gene);
        }
    }

    public void initialize()
            throws Exception {
        /* creates the instance from skeleton */
        gene = es.select();
        solver = new Solver(skeleton, gene);
        printStats(skeleton, gene);

        /* makes the decision first */
        if (decision != 0) {
            /*
            logger.debug("propagating " + SAT.toDimacs(decision) +
                         " on " + solver); */
            String instance = solver.toString();
            solver.propagate(decision);
            if (solver.isContradiction()) {
                /* TODO: this is an error an should be treated as such */
                logger.error("Propagation of " + SAT.toDimacs(decision) +
                             " on " + instance + " returned in contradiction");
                System.exit(1);
            }
        }

        // logger.debug("solving formula " + instance);
        lookahead = solver.lookahead();

        if (lookahead == 0) {
            if (solver.isSatisfied()) {
                // logger.debug("instance is satisfiable");
                cohort.send(identifier(), listener, solver.model());
            } else if (solver.isContradiction()) {
                // logger.debug("instance is a contradiction");
            } else {
                logger.error(
                        "Instance " + this + " was not satisfied and is not a " +
                        "contradiction, but lookahead didn't find any " +
                        "variable to branch on.");
                while (true);
            }

            /* job finished, decrease counter */
            finish();
            cohort.send(identifier(), counter, -1);
        } else {
            cohort.send(identifier(), counter, 2);
            suspend();
        }
    }

    public void process(Event event)
            throws Exception {
        boolean stopped = ((MessageEvent<Boolean>)event).message;

        if (!stopped) {
            // logger.debug("branching on " + lookahead + " for " + instance);
            Skeleton skeleton = solver.skeleton();
            es.evaluate(gene, skeleton.numUnits());

            cohort.submit(new CohortJob(
                    counter, listener, skeleton, lookahead * 2 + 0));
            cohort.submit(new CohortJob(
                    counter, listener, skeleton, lookahead * 2 + 1));
        }

        /* job finished, decrease counter */
        finish();
        cohort.send(identifier(), counter, -1);
    }

    public void cleanup()
            throws Exception {
    }

    public void cancel()
            throws Exception {
    }

    public String toString() {
        return "CohortJob: solver = " + solver +
               (decision == 0 ? "" : ", decision = " + SAT.toDimacs(decision));
    }
}

