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
        if (!has(key)) put(key, value);
        else value = get(key);
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

    protected int rehash() {
        int size = super.rehash();
        E[] stackValues_ = stackValues;
        stackValues = (E[])new Object[size];
        System.arraycopy(stackValues_, 0, stackValues, 0, numElements);
        return size;
    }
}
