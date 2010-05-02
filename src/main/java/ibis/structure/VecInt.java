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

    public int getAt(int index) {
        if (0 > index || index >= numElements)
            throw new IndexOutOfBoundsException();
        return array[index];
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
