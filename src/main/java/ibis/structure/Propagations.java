package ibis.structure;


public class Propagations {
    SetInt units = new SetInt();
    VecInt binaries = new VecInt();

    /**
     * Adds a new unit.
     */
    public void addUnit(int l0) {
        units.push(l0);
    }

    /**
     * Adds a new binary.
     */
    public void addBinary(int l0, int l1) {
        binaries.push(l0);
        binaries.push(l1);
    }

    /**
     * Counts number of units.
     */
    public int numUnits() {
        return units.size();
    }

    /**
     * Counts number of valid binaries.
     */
    public int numBinaries() {
        // seems that counting valid binaries
        // is not worth it
        /*
        int numBinaries = 0;
        for (int b = 0; b < binaries.size(); b += 2)
            if (!units.has(binaries.getAt(b + 0)) &&
                    !units.has(binaries.getAt(b + 1)))
                ++numBinaries;
        */
        return binaries.size();
    }
}
