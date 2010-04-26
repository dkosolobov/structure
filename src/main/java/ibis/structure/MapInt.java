package ibis.structure;

public final class MapInt<E> extends HashInt {
    private E[] stackValues;

    public MapInt() {
        super();
    }

    public MapInt(int size) {
        super(size);
    }

    public E get(int key) {
        int index = search(key);
        if (keys[index] == key)
            return stackValues[keys[index]];
        return null;
    }

    public E get(int key, E default_) {
        int index = search(key);
        if (keys[index] == key)
            return stackValues[keys[index]];
        return default_;
    }

    public int push(int key, E value) {
        int index = insert(key);
        stackValues[index] = value;
        return index;
    }

    public E peekValue() {
        return stackValues[numElements - 1];
    }

    protected int init(int size) {
        size = super.init(size);
        stackValues = (E[])new Object[size];
        return size;
    }

    protected int rehash() {
        int size = super.rehash();
        E[] stackValues_ = stackValues;
        stackValues = (E[])new Object[size];
        System.arraycopy(stackValues_, 0, stackValues, 0, numElements);
        return size;
    }
}
