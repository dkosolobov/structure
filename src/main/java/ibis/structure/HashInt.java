package ibis.structure;

public class HashInt {
    protected static final int SENTINEL = -1;

    protected int[] keys;
    protected int[] stackKeys;

    protected int mask;
    protected int numElements;

    protected HashInt() {
        init(4);
    }

    protected HashInt(int size) {
        init(size);
    }

    public final int size() {
        return numElements;
    }

    public final boolean isEmpty() {
        return numElements == 0;
    }

    public final boolean has(int key) {
        return keys[search(key)] != SENTINEL;
    }

    public final int peekKey() {
        return stackKeys[numElements - 1];
    }

    public final int[] keys() {
        int[] keys_ = new int[numElements];
        System.arraycopy(stackKeys, 0, keys_, 0, numElements);
        return keys_;
    }

    public final void pop() {
        keys[search(stackKeys[--numElements])] = SENTINEL;
    }

    public final void pop(int count) {
        for (int i = 0; i < count; ++i)
            pop();
    }

    protected final int hash(int value) {
        int a = value;
        a = (a+0x7ed55d16) + (a<<12);
        a = (a^0xc761c23c) ^ (a>>19);
        a = (a+0x165667b1) + (a<<5);
        a = (a+0xd3a2646c) ^ (a<<9);
        a = (a+0xfd7046c5) + (a<<3);
        a = (a^0xb55a4f09) ^ (a>>16);
        return a & mask;
    }

    protected final int insert(int key) {
        int hash = search(key);
        if (keys[hash] != SENTINEL)
            return stackKeys[keys[hash]];

        keys[hash] = numElements;
        stackKeys[numElements] = key;

        ++numElements;
        if (numElements * 2 > keys.length)
            rehash();

        return numElements - 1;
    }

    protected final int search(int key) {
        int hash = hash(key);
        while (true) {
            if (keys[hash] == SENTINEL)
                return hash;
            if (stackKeys[keys[hash]] == key)
                return hash;

            ++hash;
            if (hash == keys.length)
                hash = 0;
        }
    }

    private final void create(int size) {
        keys = new int[size];
        stackKeys = new int[size];
        mask = size - 1;

        for (int i = 0; i < size; ++i)
            keys[i] = SENTINEL;
    }

    protected int init(int size) {
        int log = 0;
        while ((1 << log) < size)
            ++log;
        size = 1 << log;

        this.create(size);
        this.numElements = 0;
        return size;
    }

    protected int rehash() {
        int size = 2 * keys.length;

        int[] keys_ = keys;
        int[] stackKeys_ = stackKeys;

        create(size);
        for (int i = 0; i < numElements; ++i) {
            int hash = search(stackKeys_[i]);
            keys[hash] = i;
        }
        System.arraycopy(stackKeys_, 0, stackKeys, 0, numElements);

        return size;
    }
}
