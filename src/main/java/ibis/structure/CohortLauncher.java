package ibis.structure;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.Queue;
import java.util.Vector;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Stack;
import java.util.Random;

import org.sat4j.core.ASolverFactory;
import org.sat4j.core.VecInt;
import org.sat4j.ExitCode;
import org.sat4j.minisat.core.IOrder;
import org.sat4j.minisat.core.Solver;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.reader.InstanceReader;
import org.sat4j.reader.ParseFormatException;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IProblem;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.IVecInt;

import org.apache.log4j.Logger;

import ibis.cohort.Activity;
import ibis.cohort.ActivityIdentifier;
import ibis.cohort.Cohort;
import ibis.cohort.CohortFactory;
import ibis.cohort.Event;
import ibis.cohort.MessageEvent;
import ibis.cohort.MultiEventCollector;



/**
 * Very simple launcher, to be used during the SAT competition or the SAT race
 * for instance.
 * 
 * @author Alexandru Moșoi
 * 
 */
public class CohortLauncher {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_MAX_JOBS = 1024;

    private Cohort cohort;
    private String problemName;
    private int maxJobs = DEFAULT_MAX_JOBS;

    private static StructureLogger logger = StructureLogger.getLogger(CohortLauncher.class);

    public Cohort cohort() {
        return this.cohort;
    }

    private static void displayLicense() {
        logger.info("SAT4J: a SATisfiability library for Java (c) 2004-2008 Daniel Le Berre"); //$NON-NLS-1$
        logger.info("This is free software under the dual EPL/GNU LGPL licenses."); //$NON-NLS-1$
        logger.info("See www.sat4j.org for details.");
    }

    private static void displayHeader() {
        displayLicense();

        Runtime runtime = Runtime.getRuntime();
        logger.info("Free memory \t\t" + runtime.freeMemory());
        logger.info("Max memory \t\t" + runtime.maxMemory());
        logger.info("Total memory \t\t" + runtime.totalMemory());
        logger.info("Number of processors \t" + runtime.availableProcessors());
    }

    /**
     * Parses command line arguments and setups cohort and problem name
     *
     * @return true if no error occured
     */
    public boolean configure(String[] args) {
        problemName = null;

        try {
            int index = 0;

            // creates the cohort
            cohort = CohortFactory.createCohort();
            if (cohort == null) {
                logger.error("Unknown Cohort implementation");
                return false;
            }

            // reads the number of jobs
            if (args[index].equals("-jobs")) {
                index++;
                maxJobs = Integer.parseInt(args[index++]);
            }

            // reads the problem name
            problemName = args[index++];
            cohort.activate();
            return true;
        } catch (ArrayIndexOutOfBoundsException e) {
           usage();
        } catch (ibis.ipl.IbisConfigurationException e) {
           logger.error("Cannot connect to IBIS");
        } catch (Exception e) {
            /* TODO: better handle the error */
            e.printStackTrace();
        }

        // error occured
        return false;
    }

    public void usage() {
        logger.info("Usage:");
        logger.info("\tjava -Dibis.cohort.impl=IMPL -jar lib/structure.jar [-jobs JOBS] INPUT");
        logger.info("");
        logger.info("where IMPL can be mt for multithreaded Cohort or dist for distributed Cohort");
        logger.info("      JOBS is the maximum number of jobs to be created");
        logger.info("      INPUT is the path to the problem to be solved");
    }

    public ExitCode run() {
        displayHeader();

        try {
            long startTime = System.currentTimeMillis();

            // creates the solver
            Solver<?> solver = (Solver<?>)CohortJob.readProblem(problemName);
            logger.debug("solving " + problemName);
            logger.debug("#vars     " + solver.nVars());
            logger.debug("#constraints  " + solver.nConstraints());

            // creates a random order
            Vector<Integer> order = new Vector<Integer>();
            for (int i = solver.nVars(); i > 0; --i)
                order.add(i << 1);
            Collections.shuffle(order, new Random(1));

            /* creates MAX_JOBS jobs */
            Queue<Stack<Integer>> queue = new LinkedList<Stack<Integer>>();
            queue.add(new Stack<Integer>());
            while (!queue.isEmpty() && queue.size() < maxJobs) {
                Stack<Integer> assumps = queue.remove();

                // makes all the assumptions
                solver.reset();
                solver.init();
                solver.propagate();
                for (Integer lit_: assumps) {
                    int lit = order.get(lit_ >> 1) | (lit_ & 1);
                    solver.assume(lit);
                    solver.propagate();
                }

                // finds next unassigned variable
                int next = assumps.isEmpty() ? 0 : (assumps.peek() | 1) + 1;
                while (next < 2 * order.size()) {
                    int lit = order.get(next >> 1);
                    if (solver.getVocabulary().isUnassigned(lit))
                        break;
                    next += 2;
                }

                if (next == 2 * order.size()) {
                    // TODO, just solved but the solution is slightly harder to generate
                    logger.warn("**** solved (but I won't tell you the answer) *****");
                } else {
                    // branch job
                    int lit = order.get(next >> 1);
                    Stack<Integer> temp;

                    for (int i = 0; i < 2; ++i) {
                        solver.assume(lit | i);
                        if (solver.propagate() == null) {
                            temp = (Stack<Integer>)assumps.clone();
                            temp.push(next | i);
                            queue.add(temp);
                        }
                        solver.unset(lit | i);
                    }
                }
            }

            long generationTime = System.currentTimeMillis();

            /* submits jobs */
            MultiEventCollector root = new MultiEventCollector(queue.size());
            cohort.submit(root);

            for (Stack<Integer> assumps: queue) {
                IVecInt temp = new VecInt();
                for (Integer l: assumps)  // transforms to DIMACS representation
                    temp.push(((l & 1) == 1 ? -1 : 1) * (order.get(l >> 1) >> 1));
                cohort.submit(new CohortJob(root.identifier(), problemName, temp));
            }

            /* waits jobs */
            Event[] events = root.waitForEvents();

            /* prints some useful statistics */
            long endTime = System.currentTimeMillis();
            logger.info("Queue generation took " + (generationTime - startTime) +
                    " millisecond(s) (marker: byuaynhxamgepnwizbnkyaix)");
            logger.info("Processing the queue took " + (endTime - generationTime) +
                    " millisecond(s) (marker: dvalixdwwyupxhhworcwnikq)");
            logger.info("Elapsed time " + (endTime - startTime) +
                    " millisecond(s) (marker: mtsrwhdutmvnelwyajizogkf)");

            boolean solved = false;
            for (Event event: events) {
                int[] model = ((MessageEvent<int[]>)event).message;
                if (model != null) {
                    solved = true;
                    logger.solution(model);
                }
            }

            if (!solved) {
                logger.answer(ExitCode.UNSATISFIABLE);
                return ExitCode.UNSATISFIABLE;
            } else {
                logger.answer(ExitCode.SATISFIABLE);
                return ExitCode.SATISFIABLE;
            }
        } catch (FileNotFoundException e) {
            logger.fatal("Cannot open input file", e);
        } catch (IOException e) {
            logger.fatal("Cannot read input file", e);
        } catch (ParseFormatException e) {
            logger.fatal("Error while interpreting the input file", e);
        } catch (ContradictionException e) {
            logger.info("(trivial inconsistency)");
            logger.answer(ExitCode.UNSATISFIABLE);
            return ExitCode.UNSATISFIABLE;
        }

        return ExitCode.UNKNOWN;
    }


    public static void main(final String[] args) {
        CohortLauncher launcher = new CohortLauncher();
        ExitCode exitCode = ExitCode.UNKNOWN;

        if (!launcher.configure(args)) {
            /* wrong arguments, or error occured */
        } else if (launcher.cohort().isMaster()) {
            /* master */
            System.out.println("Starting as master!");
            exitCode = launcher.run();
            launcher.cohort.done();
        } else {
            /* slave */
            System.out.println("Starting as slave!");
            launcher.cohort.done();
        }

        System.exit(exitCode.value());
    }
}
