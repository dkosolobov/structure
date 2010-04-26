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
}
