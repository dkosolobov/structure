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
import org.sat4j.reader.Reader;
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
import ibis.cohort.Context;
import ibis.cohort.CohortFactory;
import ibis.cohort.Event;
import ibis.cohort.MessageEvent;
import ibis.cohort.MultiEventCollector;
import ibis.cohort.SingleEventCollector;



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

    public void start()
            throws FileNotFoundException, ParseFormatException, IOException, ContradictionException {
        logger.debug("starting " + cohort);
        if (cohort.isMaster()) {
            displayHeader();

            SATInstance instance = readProblem(problemName);
            logger.debug("solving " + problemName);
            logger.debug("#vars     " + instance.nVars());
            logger.debug("#constraints  " + instance.nConstraints());

            JobsCounter counter = new JobsCounter(1);
            SolutionListener listener = new SolutionListener(counter);

            /* starts the first job */
            cohort.submit(counter);
            cohort.submit(listener);
            cohort.submit(new CohortJob(
                    counter.identifier(), listener.identifier(), instance, 0));

            /* waits for all jobs to finish */
            synchronized(counter) {
                while (counter.counter() > 0) {
                    try { counter.wait(); }
                    catch (InterruptedException e) { /* ignored */ }
                }
            }
            listener.finish();
        }

        cohort.done();
    }

    /**
     * Reads the SATInstance from problemName
     */
    public static SATInstance readProblem(String problemName)
            throws FileNotFoundException, ParseFormatException, IOException, ContradictionException {
        SATInstance satInstance = new SATInstance();
        Reader reader = new InstanceReader(satInstance);
        reader.parseInstance(problemName);
        return satInstance;
    }

    public static void main(final String[] args) {
        CohortLauncher launcher = new CohortLauncher();
        ExitCode exitCode = ExitCode.UNKNOWN;

        try {
            if (!launcher.configure(args)) {
                /* wrong arguments, or error occured */
            } else {
                /* good to go */
                launcher.start();
            }
        } catch (FileNotFoundException e) {
            logger.fatal("Cannot open input file", e);
        } catch (ParseFormatException e) {
            logger.fatal("Error while interpreting the input file", e);
        } catch (IOException e) {
            logger.fatal("Cannot read input file", e);
        } catch (ContradictionException e) {
            logger.info("(trivial inconsistency)");
            logger.answer(ExitCode.UNSATISFIABLE);
            exitCode = ExitCode.UNSATISFIABLE;
        } catch (Exception e) {
            /* FIXME: never throw Exception */
            logger.fatal("Unexpected error received (FIXME)", e);
        }

        System.exit(exitCode.value());
    }
}


class SolutionListener extends Activity {
    private static StructureLogger logger = StructureLogger.getLogger(SolutionListener.class);

    private final JobsCounter counter;

    public SolutionListener(JobsCounter counter) {
        super(Context.ANY);
        this.counter = counter;
    }

    public void initialize()
            throws Exception {
        suspend();
    }

    public void process(Event e)
            throws Exception {
        counter.stop();
        int[] model = ((MessageEvent<int[]>)e).message;
        logger.solution(model);
        suspend();
    }

    public void cleanup()
            throws Exception {
    }

    public void cancel()
            throws Exception {
    }
}


class JobsCounter extends Activity {
    private int counter;
    private boolean stopped = false;

    public JobsCounter(int counter) {
        super(Context.ANY);
        this.counter = counter;
    }

    public synchronized int counter() {
        return counter;
    }

    public void initialize()
            throws Exception {
        suspend();
    }

    public synchronized void process(Event e)
            throws Exception {
        int inc = ((MessageEvent<Integer>)e).message;

        if (inc > 0) {
            /* job is branching returns whether we should stop */
            cohort.send(identifier(), e.source, stopped);
        }

        if (inc > 0 && stopped) {
            /* if processes stopped job is not branching */
            suspend();
            return;
        }

        counter += inc;

        if (counter == 0) {
            notifyAll();
            finish();
        } else {
            suspend();
        }
    }

    public void cleanup()
            throws Exception {
    }

    public void cancel()
            throws Exception {
    }

    public synchronized void stop() {
        System.err.println("stopped");
        stopped = true;
    }
}

