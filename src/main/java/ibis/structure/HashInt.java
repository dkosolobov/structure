package ibis.structure;

import java.util.Arrays;


public class HashInt {
    protected static final int SENTINEL = -1;
    protected static final double LOAD_FACTOR = 0.75;

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

    /**
     * Hash function from Bob Jenkins.
     */
    protected final int hash(int value) {
        int a = value;
        a = (a+0x7ed55d16) + (a<<12);
        a = (a^0xc761c23c) ^ (a>>19);
        a = (a+0x165667b1) + (a<<5);
        a = (a+0xd3a2646c) ^ (a<<9);
        a = (a+0xfd7046c5) + (a<<3);
        a = (a^0xb55a4f09) ^ (a>>16);
        return a;
    }

    /**
     * Hash function from Java.
     */
    private static int newHash(int h) {
        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    protected final int insertNoLookup(int key, int hash) {
        if (keys[hash] != SENTINEL)
            return keys[hash];

        ++numElements;

        // puts the key in the stack
        if (numElements == stackKeys.length)
            restack();
        stackKeys[numElements - 1] = key;

        // puts the key in the hash table
        keys[hash] = numElements - 1;
        if (numElements >= LOAD_FACTOR * keys.length)
            rehash();

        return numElements - 1;
    }

    protected final int insert(int key) {
        return insertNoLookup(key, search(key));
    }

    protected final int search(int key) {
        int hash = newHash(key) & mask;
        int step = 1;

        while (keys[hash] != SENTINEL &&
                stackKeys[keys[hash]] != key) {
            hash = (hash + (step * (step + 1) >> 1)) & mask;
        }

        return hash;
    }

    protected int init(int size) {
        int log = 0;
        while ((1 << log) < size)
            ++log;
        size = 1 << log;

        this.keys = new int[size];
        this.stackKeys = new int[size];
        this.mask = keys.length - 1;
        this.numElements = 0;
        Arrays.fill(this.keys, SENTINEL);

        return size;
    }

    protected void restack() {
        stackKeys = Arrays.copyOf(stackKeys, 2 * stackKeys.length);
    }

    protected final int rehash() {
        keys = new int[2 * keys.length];
        mask = keys.length - 1;
        Arrays.fill(keys, SENTINEL);

        for (int i = 0; i < numElements; ++i)
            keys[search(stackKeys[i])] = i;

        return keys.length;
    }
}
