package ibis.structure;

import java.util.Arrays;


// from MapInt<E>:
//    replace MapInt<E> with MapIntInt
//    replace E with int
public final class MapIntInt extends HashInt {
    private int[] stackValues;

    public MapIntInt() {
        super();
    }

    public int get(int key) {
        return get(key, 0);
    }

    public int get(int key, int default_) {
        int index = search(key);
        if (keys[index] != SENTINEL)
            default_ = stackValues[keys[index]];
        return default_;
    }

    public int put(int key, int value) {
        int index = insert(key);
        stackValues[index] = value;
        return index;
    }

    public int setdefault(int key, int value) {
        int hash = search(key);
        if (keys[hash] != SENTINEL)
            return stackValues[keys[hash]];

        int index = insertNoLookup(key, hash);
        stackValues[index] = value;
        return value;
    }

    public int peekValue() {
        return stackValues[numElements - 1];
    }

    protected int init(int size) {
        size = super.init(size);
        stackValues = new int[size];
        return size;
    }

    protected void restack() {
        super.restack();
        stackValues = Arrays.copyOf(stackValues, 2 * stackValues.length);
    }
}
