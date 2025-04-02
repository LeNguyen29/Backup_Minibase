package global;

import java.nio.ByteBuffer;

public class Vector100Dtype {
    public static final int SIZE = 100; // 100 dimensions
    public static final short MIN_VALUE = -10000;
    public static final short MAX_VALUE = 10000;

    private short[] vector;

    // default constructor
    public Vector100Dtype() {
        vector = new short[SIZE];
    }

    // constructor
    public Vector100Dtype(short[] vector) {
        if (vector.length != SIZE || vector == null) {
            throw new IllegalArgumentException("Vector must have " + SIZE + " elements");
        }
        this.vector = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            short val = vector[i];
            if (val < MIN_VALUE || val > MAX_VALUE) {
                throw new IllegalArgumentException("Value at index " + i + " is out of range: " + val);
            }
            this.vector[i] = val;
        }
    }

    // getter
    public short[] getVector100D() {
        // System.out.println("vector clone: " + vector.clone());
        return vector.clone();
    }

    // setter
    public void setVector100D(short[] vector) {
        if (vector.length != SIZE || vector == null) {
            throw new IllegalArgumentException("Vector must have " + SIZE + " elements");
        }
        for (int i = 0; i < SIZE; i++) {
            short val = vector[i];
            if (val < MIN_VALUE || val > MAX_VALUE) {
                throw new IllegalArgumentException("Value at index " + i + " is out of range: " + val);
            }
        }
        this.vector = vector.clone();
    }

    // get specific element
    public short getVector100DElement(int index) {
        if (index < 0 || index >= SIZE) {
            throw new IndexOutOfBoundsException("Index must be between 0 and " + (SIZE - 1));
        }
        return vector[index];
    }

    // set specific element
    public void setVector100DElement(int index, short value) {
        if (index < 0 || index >= SIZE) {
            throw new IndexOutOfBoundsException("Index must be between 0 and " + (SIZE - 1));
        }
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException("Value must be between " + MIN_VALUE + " and " + MAX_VALUE);
        }
        vector[index] = value;
    }

    public byte[] toByteArray() {
        // Allocate a byte array of size 2 * SIZE (each short is 2 bytes)
        byte[] byteArray = new byte[SIZE * Short.BYTES];
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);

        // Write each short value into the byte buffer
        for (short value : vector) {
            buffer.putShort(value);
        }

        return byteArray;
    }

    public double computeDistance(Vector100Dtype other) {
        if (other == null || other.vector.length != this.vector.length) {
            throw new IllegalArgumentException("Vectors must have the same length.");
        }

        double sum = 0.0;
        for (int i = 0; i < this.vector.length; i++) {
            double diff = this.vector[i] - other.vector[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
