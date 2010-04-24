package ibis.structure;


public enum Value {
    TRUE(0), FALSE(1), UNKNOWN(2);

    private final int intValue;

    private Value(int intValue) {
        this.intValue = intValue;        
    }

    public int intValue() {
        return intValue;
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    public static Value fromInt(int intValue) {
        if (intValue == 0) return TRUE;
        if (intValue == 1) return FALSE;
        if (intValue == 2) return UNKNOWN;
        throw new IllegalArgumentException();
    }
}
