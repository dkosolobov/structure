package org.sat4j;

import java.io.PrintWriter;


public class Logger {
	public static final String SOLUTION_PREFIX = "v ";
    public static final String ANSWER_PREFIX = "s ";
    public static final String COMMENT_PREFIX = "c ";
    public static final PrintWriter out = new PrintWriter(System.out, true);

    public static void log(String message) {
        out.println(COMMENT_PREFIX + message);
    }

    public static void answer(String message) {
        out.println(ANSWER_PREFIX + message);
    }

    public static void solution(int[] model) {
        out.print(SOLUTION_PREFIX);
        for (int i = 0; i < model.length; ++i)
            out.print(" " + model[i]);
        out.println();
    }
}
