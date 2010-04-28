package ibis.structure;


import java.util.Vector;
import java.io.Serializable;


public final class Skeleton implements Serializable {
    public int numVariables;
    public int[][] units;
    public int[][] binaries;      /* two parallel vectors */
    public int[][] ternaries;     /* three parallel vector */
}
