package org.sat4j;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.Queue;
import java.util.Vector;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Stack;

import org.sat4j.core.VecInt;

import org.sat4j.core.ASolverFactory;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.minisat.core.IOrder;
import org.sat4j.minisat.core.Solver;
import org.sat4j.reader.InstanceReader;
import org.sat4j.reader.ParseFormatException;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IProblem;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.IVecInt;

import ibis.cohort.Activity;
import ibis.cohort.ActivityIdentifier;
import ibis.cohort.Cohort;
import ibis.cohort.Event;
import ibis.cohort.MessageEvent;
import ibis.cohort.MultiEventCollector;

import ibis.cohort.impl.distributed.DistributedCohort;
import ibis.cohort.impl.multithreaded.MTCohort;
import ibis.cohort.impl.sequential.Sequential;


/**
 * Very simple launcher, to be used during the SAT competition or the SAT race
 * for instance.
 * 
 * @author Alexandru Moșoi
 * 
 */
public class CohortLauncher {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_MAX_JOBS = 32;   /* TODO: INCREASE */

    private ExitCode exitCode = ExitCode.UNKNOWN;
    private Cohort cohort;
    private String problemName;
    private int maxJobs = DEFAULT_MAX_JOBS;

    public Cohort cohort() {
        return this.cohort;
    }

    private static void displayLicense() {
        Logger.log("SAT4J: a SATisfiability library for Java (c) 2004-2008 Daniel Le Berre"); //$NON-NLS-1$
        Logger.log("This is free software under the dual EPL/GNU LGPL licenses."); //$NON-NLS-1$
        Logger.log("See www.sat4j.org for details.");
    }

    private static void displayHeader() {
        displayLicense();

        Runtime runtime = Runtime.getRuntime();
        Logger.log("Free memory \t\t" + runtime.freeMemory());
        Logger.log("Max memory \t\t" + runtime.maxMemory());
        Logger.log("Total memory \t\t" + runtime.totalMemory());
        Logger.log("Number of processors \t" + runtime.availableProcessors());
    }

    /**
     * if arguments are parsed correctly sets `problemName` to the
     * path of the problem to solve otherwise `problemName` is null.
     *
     * FIXME: ugly trick
     */
    public void configure(String[] args) {
        problemName = null;

        try {
            int index = 0;

            // selects COHORT implementation
            if (args[index].equals("-seq")) {
                index++;
                cohort = new Sequential();
                Logger.log("Using SEQUENTIAL Cohort implementation");
            } else if (args[index].equals("-mt")) {
                index++;
                int threads = Integer.parseInt(args[index++]);
                cohort = new MTCohort(threads);
                Logger.log("Using MULTITHREADED(" + threads + ") Cohort implementation");
            } else if (args[index].equals("-dist")) {
                index++;
                cohort = new DistributedCohort(new Properties());
                Logger.log("Using DISTRIBUTED Cohort implementation");
            } else {
                System.out.println("Unknown Cohort implementation selected!");
                return;
            }

            // reads number of jobs
            if (args[index].equals("-jobs")) {
                index++;
                maxJobs = Integer.parseInt(args[index++]);
            }

            // reads the problem name
            problemName = args[index++];
        } catch (ArrayIndexOutOfBoundsException e) {
            /* ignore lack of parameters */
        } catch (Exception e) {
            /* TODO: better handle the error */
            e.printStackTrace();
        }
    }

    public void usage() {
        Logger.log("java -jar structure.jar [-seq | -mt num_threads | -dist] [-jobs num_jobs] <cnffile>");
    }

    public void run() {
        displayHeader();

        try {
            // creates the solver
            Solver<?> solver = (Solver<?>)CohortJob.readProblem(problemName);
            Logger.log("solving " + problemName);
            Logger.log("#vars     " + solver.nVars()); //$NON-NLS-1$
            Logger.log("#constraints  " + solver.nConstraints()); //$NON-NLS-1$

            // creates a random order
            Vector<Integer> order = new Vector<Integer>();
            for (int i = solver.nVars(); i > 0; --i)
                order.add(i << 1);
            Collections.shuffle(order);

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
                while (next <= 2 * order.size()) {
                    int lit = order.get(next >> 1);
                    if (solver.getVocabulary().isUnassigned(lit))
                        break;
                    next += 2;
                }

                if (next == 2 * order.size()) {
                    // TODO, just solved
                    Logger.log("**** solved *****");
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
            boolean solved = false;
            for (Event event: events) {
                int[] model = ((MessageEvent<int[]>)event).message;
                if (model != null) {
                    if (!solved) {
                        Logger.answer("" + ExitCode.SATISFIABLE);
                        solved = true;
                    }
                    Logger.solution(model);
                }
            }

            if (!solved) {
                Logger.answer("" + ExitCode.UNSATISFIABLE);
                exitCode = ExitCode.SATISFIABLE;
            }

            cohort.done();
        } catch (FileNotFoundException e) {
            System.err.println("FATAL " + e.getLocalizedMessage());
        } catch (IOException e) {
            System.err.println("FATAL " + e.getLocalizedMessage());
        } catch (ContradictionException e) {
            exitCode = ExitCode.UNSATISFIABLE;
            Logger.log("(trivial inconsistency)");
        } catch (ParseFormatException e) {
            System.err.println("FATAL " + e.getLocalizedMessage());
        }
    }


    public static void main(final String[] args) {
        CohortLauncher launcher = new CohortLauncher();

        launcher.configure(args);
        if (launcher.problemName == null) {
            /* wrong arguments */
            launcher.usage();
        } else if (launcher.cohort().isMaster()) {
            /* master */
            System.out.println("Starting as master!");
            launcher.run();
        } else {
            /* slave */
            System.out.println("Starting as Slave!");
            try { while (true) Thread.sleep(10000); }
            catch (InterruptedException e) { }
        }

        System.exit(launcher.getExitCode().value());
    }

    /**
     * Gets the exitCode for this instance.
     *
     * @return The exitCode.
     */
    public ExitCode getExitCode()
    {
        return this.exitCode;
    }

}
