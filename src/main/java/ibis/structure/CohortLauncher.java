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

import org.sat4j.core.ASolverFactory;
import org.sat4j.core.VecInt;
import org.sat4j.ExitCode;
import org.sat4j.minisat.core.IOrder;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.reader.InstanceReader;
import org.sat4j.reader.ParseFormatException;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IProblem;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.IVecInt;


/**
 * Very simple launcher, to be used during the SAT competition or the SAT race
 * for instance.
 * 
 * @author Alexandru Moșoi
 * 
 */
public class CohortLauncher {
    private static Logger logger = Logger.getLogger(CohortLauncher.class);

    private Cohort cohort;
    private String problemName;


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

            // reads the problem
            Skeleton instance = readProblem(problemName);
            Solver solver = new Solver(instance, null);

            logger.debug("solving " + problemName);
            logger.debug("#vars     " + instance.numVariables());
            logger.debug("#constraints  " + instance.numConstraints());
            logger.debug("#solved  " + instance.numUnits());

            // simplifies
            solver.simplify();
            Skeleton easy = solver.skeleton();

            logger.debug("simplifying");
            logger.debug("#vars     " + easy.numVariables());
            logger.debug("#constraints  " + easy.numConstraints());
            logger.debug("#solved  " + easy.numUnits());

            JobsCounter counter = new JobsCounter(1);
            SolutionListener listener = new SolutionListener(instance, counter);

            /* starts the first job */
            cohort.submit(counter);
            cohort.submit(listener);
            cohort.submit(new CohortJob(
                    counter.identifier(), listener.identifier(), easy, 0));

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
    public static Skeleton readProblem(String problemName)
            throws FileNotFoundException, ParseFormatException, IOException, ContradictionException {
        Reader instance = new Reader();
        org.sat4j.reader.Reader reader = new InstanceReader(instance);
        reader.parseInstance(problemName);
        return instance.skeleton();
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
            /* TODO */
            // logger.answer(ExitCode.UNSATISFIABLE);
            exitCode = ExitCode.UNSATISFIABLE;
        } catch (Exception e) {
            /* FIXME: never throw Exception */
            logger.fatal("Unexpected error received (FIXME)", e);
        }

        System.exit(exitCode.value());
    }
}


class SolutionListener extends Activity {
    private static Logger logger = Logger.getLogger(SolutionListener.class);

    private final Skeleton instance;
    private final JobsCounter counter;

    public SolutionListener(Skeleton instance, JobsCounter counter) {
        super(Context.ANY);
        this.counter = counter;
        this.instance = instance;
    }

    public void initialize()
            throws Exception {
        suspend();
    }

    public void process(Event e)
            throws Exception {
        counter.stop();
        printModel(((MessageEvent<int[]>)e).message);
        suspend();
    }

    public void cleanup()
            throws Exception {
    }

    public void cancel()
            throws Exception {
    }

    private void printModel(int[] model) {
        if (instance.testModel(model))
            System.out.println("c model is CORRECT");
        else {
            System.out.println("c model is WRONG");
            while (true);
        }

        System.out.print("v");
        for (int i = 0; i < model.length; ++i)
            System.out.print(" " + SAT.toDimacs(model[i]));
        System.out.println();
    }
}


class JobsCounter extends Activity {
    private static Logger logger = Logger.getLogger(CohortLauncher.class);

    private int counter;
    private long timer = System.currentTimeMillis();


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

    private int stats = 0;

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

        if (inc < 0) {
            if (++stats % 256 == 0) {
                long now = System.currentTimeMillis();
                logger.debug("solved " + stats +
                             ", pending " + counter +
                             ", speed " + (256. / ((now - timer) / 1000.)));
                timer = now;
            }
        }

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

