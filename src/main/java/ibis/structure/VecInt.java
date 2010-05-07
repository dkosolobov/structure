package ibis.structure;

public final class VecInt {
    public static final VecInt EMPTY = new VecInt();

    int[] array;
    int numElements;

    public VecInt() {
        init(4);
    }

    public VecInt(int capacity) {
        init(capacity);
    }

    public int size() {
        return numElements;
    }

    public boolean isEmpty() {
        return numElements == 0;
    }

    public void push(int e) {
        if (numElements == array.length)
            resize(numElements * 2);
        array[numElements++] = e;
    }

    public void pushAll(VecInt e) {
        // resizes array, keeping it a POT
        int size = 1;
        while (numElements + e.numElements > size)
            size *= 2;
        if (size != array.length)
            resize(size);

        System.arraycopy(e.array, 0, array, numElements, e.numElements);
        numElements += e.numElements;
    }

    public int pop() {
        if (numElements == 0)
            throw new IndexOutOfBoundsException();
        return array[--numElements];
    }

    /**
     * Returns a specific element.
     *
     * @param index location of the element
     * @return element at index
     * @throws IndexOutOfBoundsException if location is invalid
     */
    public int getAt(int index) {
        if (0 > index || index >= numElements)
            throw new IndexOutOfBoundsException();
        return array[index];
    }

    /**
     * Returns a copy of this vector as an int array.
     */
    public int[] toArray() {
        int[] elements = new int[numElements];
        System.arraycopy(array, 0, elements, 0, numElements);
        return elements;
    }

    public void resize(int size) {
        if (size < numElements)
            numElements = size;

        int[] array_ = new int[size];
        System.arraycopy(array, 0, array_, 0, numElements);
        array = array_;
    }

    private void init(int capacity) {
        array = new int[capacity];
        numElements = 0;
    }

}
