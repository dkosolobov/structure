package ibis.structure;

import java.io.PrintWriter;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerFactory;

import org.sat4j.ExitCode;


public class StructureLogger extends Logger {
	private static final String SOLUTION_PREFIX = "v ";
    private static final String ANSWER_PREFIX = "s ";
    private static final String COMMENT_PREFIX = "c ";

    private static final String FQCN = StructureLogger.class.getName() + ".";
    private static final PrintWriter out = new PrintWriter(System.out, true);
    private static final LoggerFactory factory = new StructureLoggerFactory();

    /**
     * Constructor.
     *
     * Don't use directly. User getLogger(...) instead.
     */
    public StructureLogger(String name) {
        super(name);
    }

    public void answer(ExitCode exitCode) {
        out.println(ANSWER_PREFIX + exitCode);
    }

    public static void solution(int[] model) {
        out.print(SOLUTION_PREFIX);
        for (int i = 0; i < model.length; ++i)
            out.print(" " + model[i]);
        out.println();
    }

    public static StructureLogger getLogger(Class clazz) {
        return (StructureLogger)Logger.getLogger(clazz.getName(), factory);
    }

    public static StructureLogger getLogger(String name) {
        return (StructureLogger)Logger.getLogger(name, factory);
    }
}
