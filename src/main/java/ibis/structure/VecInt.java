package ibis.structure;

public final class VecInt {
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
            resize();
        array[numElements++] = e;
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

    private void init(int capacity) {
        array = new int[capacity];
        numElements = 0;
    }

    private void resize() {
        int[] array_ = new int[array.length * 2];
        System.arraycopy(array, 0, array_, 0, numElements);
        array = array_;
    }
}
