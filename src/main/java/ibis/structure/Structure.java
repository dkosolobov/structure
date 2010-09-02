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
import ibis.cohort.CohortFactory;
import ibis.cohort.Event;
import ibis.cohort.MessageEvent;
import ibis.cohort.MultiEventCollector;
import ibis.cohort.SingleEventCollector;

public class Structure {
  private static Logger logger = Logger.getLogger(Structure.class);

  private static void displayHeader() {
    logger.info("Structure: a SATisfiability library for Java (c) 2009-2010 Alexandru Moșoi");
    logger.info("This is free software under the MIT licence except where otherwise noted.");

    Runtime runtime = Runtime.getRuntime();
    logger.info("Free memory \t\t" + runtime.freeMemory());
    logger.info("Max memory \t\t" + runtime.maxMemory());
    logger.info("Total memory \t\t" + runtime.totalMemory());
    logger.info("Number of processors \t" + runtime.availableProcessors());
  }

  private static Skeleton configure(String[] args) throws Exception {
    String url = args[0];
    logger.info("Reading from " + url);
    return Reader.parseURL(url);
  }

  public static void main(String[] args) throws Exception {
    Cohort cohort = CohortFactory.createCohort();
    try {
      displayHeader();
      if (cohort.isMaster()) {
        Skeleton skeleton = configure(args);
        logger.info("Read problem, difficulty " + skeleton.difficulty());
        skeleton.canonicalize();
        logger.info("Canonicalized instace, difficulty " + skeleton.difficulty());
      }
    } finally {
      cohort.done();
    }
  }
}
