package ibis.structure;

public final class SetInt extends HashInt {
    public SetInt() {
        super();
    }

    public SetInt(int size) {
        super(size);
    }

    public int push(int key) {
        return insert(key);
    }

    public void pushAll(SetInt other) {
        for (int i = 0; i < other.numElements; ++i)
            push(other.keys[i]);
    }

    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("(");
        for (int i = 0; i < numElements; ++i) {
            if (i != 0)
                result.append(", ");
            result.append(SAT.toDimacs(stackKeys[i]));
        }
        result.append(")");
        return result.toString();
    }
}
