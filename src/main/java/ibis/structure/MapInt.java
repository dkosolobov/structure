package ibis.structure;

import java.util.Arrays;


public final class MapInt<E> extends HashInt {
    private E[] stackValues;

    public MapInt() {
        super();
    }

    public MapInt(int size) {
        super(size);
    }

    public E get(int key) {
        return get(key, null);
    }

    public E get(int key, E default_) {
        int index = search(key);
        if (keys[index] == SENTINEL)
            return default_;
        else
            return stackValues[keys[index]];
    }

    public int put(int key, E value) {
        int index = insert(key);
        stackValues[index] = value;
        return index;
    }

    public E setdefault(int key, E value) {
        int hash = search(key);
        if (keys[hash] != SENTINEL)
            return stackValues[keys[hash]];

        put(key, value);
        return value;
    }

    public E peekValue() {
        return stackValues[numElements - 1];
    }

    protected int init(int size) {
        size = super.init(size);
        stackValues = (E[])new Object[size];
        return size;
    }

    protected void restack() {
        super.restack();
        stackValues = (E[])Arrays.copyOf(stackValues, 2 * stackValues.length);
    }
}
