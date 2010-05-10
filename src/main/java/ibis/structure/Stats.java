package ibis.structure;


public class Stats {
    public int numUnits;
    public int numBinaries;

    public Stats(Propagations propagated) {
        this.numUnits = propagated.numUnits();
        this.numBinaries = propagated.numBinaries();
    }

    public double eval() {
        return (numUnits + 1) * (numBinaries + 1);
    }

    private static double sigmoid(double x) {
        return (1 / (1 + Math.exp(-x)));
    }
}
