package lshfindex;

import btree.*;
import global.*;
import java.io.File;
import java.util.PriorityQueue;
import java.util.Vector;

public class LSHFIndexFile implements GlobalConst {

    private String indexName;
    private int h; // Number of hash functions per layer
    private int L; // Number of layers
    private int[][] b; // b
    private Vector100Dtype[][] hashVectors; // Hash vector storage
    public BTreeFile[] layers; // Array of B+ trees, one for each layer

    // Constructor for creating or loading an LSHFIndexFile
    public LSHFIndexFile(String indexName, int h, int L) throws Exception {
        this.h = h;
        this.L = L;
        this.indexName = indexName;
        this.layers = new BTreeFile[L];
        b = new int[L][h];
        hashVectors = new Vector100Dtype[L][h];
        // Initialize B and hashing vectors
        for (int i = 0; i < L; i++) {
            for (int j = 0; j < h; j++) {
                b[i][j] = (int) (Math.random() * 20001) - 10000;
                short[] vectorShort = new short[100];
                for (int k = 0; k < 100; k++) {
                    vectorShort[k] = (short) ((Math.random() * 20001) - 10000);
                }
                Vector100Dtype hashVector = new Vector100Dtype(vectorShort);
                hashVectors[i][j] = hashVector;
            }
        }
        boolean indexExists = true;

        // Check if all layer files exist
        for (int i = 0; i < L; i++) {
            String layerFileName = indexName + "_layer" + i;
            File layerFile = new File(layerFileName);
            if (!layerFile.exists()) {
                indexExists = false;
                break;
            }
        }

        if (indexExists) {
            // Load existing B+ tree layers
            for (int i = 0; i < L; i++) {
                String layerFileName = indexName + "_layer" + i;
                layers[i] = new BTreeFile(layerFileName); // Load existing B+ tree
            }
            System.out.println("Loaded existing LSHFIndexFile: " + indexName);
        } else {
            // Create new B+ tree layers
            for (int i = 0; i < L; i++) {
                String layerFileName = indexName + "_layer" + i;
                layers[i] = new BTreeFile(layerFileName, AttrType.attrVector100D, Vector100Dtype.SIZE * 2, 1); // Ensure
                                                                                                               // attrVector100D
                                                                                                               // is
                                                                                                               // used
            }
            System.out.println("Created new LSHFIndexFile: " + indexName);
        }
    }

    private String getHashingBit(Vector100Dtype input, Vector100Dtype hashingVector, int b) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += hashingVector.getVectorSpecificDimensionValue(i) * input.getVectorSpecificDimensionValue(i);
        }
        if (sum + b > 0) {
            return "1";
        } else
            return "0";
    }

    // Hash function to calculate hash value for a vector
    private int calculateHashValue(Vector100Dtype vector, int layerIndex) {
        String hash = "";
        for (int i = 0; i < h; i++) {
            hash += getHashingBit(vector, hashVectors[layerIndex][i], b[layerIndex][i]);
        }
        return Integer.parseInt(hash, 2);
    }

    // Insert a vector into the LSHFIndexFile
    public void insert(Vector100Dtype vector, RID rid) throws Exception {
        for (int i = 0; i < L; i++) { // Iterate over layers
            int hashValue = calculateHashValue(vector, i); // Compute hash values for this layer
            Vector100DKey key = new Vector100DKey(vector, hashValue); // Create a key with the hash value
            layers[i].insert(key, rid); // Insert the key and RID into the B+ tree
        }
    }

    // Delete a vector from the LSHFIndexFile
    public void delete(Vector100Dtype vector, RID rid) throws Exception {
        for (int i = 0; i < L; i++) { // Iterate over layers
            int hashValues = calculateHashValue(vector, i); // Compute hash values for this layer
            Vector100DKey key = new Vector100DKey(vector, hashValues); // Create a key with the hash value
            layers[i].Delete(key, rid); // Delete the key and RID from the B+ tree
        }
    }

    // LSHFFileScan: Retrieve all RIDs stored in the index
    public Vector<RID> LSHFFileScan() {
        Vector<RID> results = new Vector<>();

        try {
            for (int i = 0; i < L; i++) {
                BTFileScan scan = layers[i].new_scan(null, null); // Full scan
                KeyDataEntry entry;

                while ((entry = scan.get_next()) != null) {
                    RID rid = ((LeafData) entry.data).getData();
                    results.add(rid);
                }

                scan.DestroyBTreeFileScan();
            }
        } catch (Exception e) {
            System.err.println("Error during LSHFFileScan: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    // LSHFFileRangeScan: Retrieve RIDs within a range across all layers
    public Vector<RID> LSHFFileRangeScan(Vector100DKey key, int distance) {
        Vector<RID> results = new Vector<>();

        try {
            for (int i = 0; i < L; i++) { // Iterate over all layers
                BTFileScan scan = layers[i].new_scan(null, null); // Full scan
                KeyDataEntry entry;

                while ((entry = scan.get_next()) != null) {
                    Vector100DKey currentKey = (Vector100DKey) entry.key;
                    if (currentKey.computeDistance(key) <= distance) {
                        RID rid = ((LeafData) entry.data).getData();
                        results.add(rid);
                    }
                }

                scan.DestroyBTreeFileScan();
            }
        } catch (Exception e) {
            System.err.println("Error during LSHFFileRangeScan: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    // LSHFFileNNScan: Retrieve the nearest neighbors across all layers
    public Vector<RID> LSHFFileNNScan(Vector100DKey key, int count) {
        PriorityQueue<KeyDataEntry> nearestNeighbors = new PriorityQueue<>(
                count,
                (a, b) -> {
                    int distA = ((Vector100DKey) a.key).computeDistance(key);
                    int distB = ((Vector100DKey) b.key).computeDistance(key);
                    return Integer.compare(distA, distB);
                });

        try {
            for (int i = 0; i < L; i++) { // Iterate over all layers
                BTFileScan scan = layers[i].new_scan(null, null); // Full scan
                KeyDataEntry entry;

                while ((entry = scan.get_next()) != null) {
                    if (nearestNeighbors.size() < count) {
                        nearestNeighbors.add(entry);
                    } else {
                        int currentDistance = ((Vector100DKey) entry.key).computeDistance(key);
                        int maxDistance = ((Vector100DKey) nearestNeighbors.peek().key).computeDistance(key);

                        if (currentDistance < maxDistance) {
                            nearestNeighbors.poll();
                            nearestNeighbors.add(entry);
                        }
                    }
                }

                scan.DestroyBTreeFileScan();
            }

            Vector<RID> results = new Vector<>();
            while (!nearestNeighbors.isEmpty()) {
                KeyDataEntry entry = nearestNeighbors.poll();
                RID rid = ((LeafData) entry.data).getData();
                results.add(rid);
            }

            return results;
        } catch (Exception e) {
            System.err.println("Error during LSHFFileNNScan: " + e.getMessage());
            e.printStackTrace();
        }

        return new Vector<>();
    }

    // Close the LSHFIndexFile
    public void close() {
        for (int i = 0; i < L; i++) {
            try {
                layers[i].close();
            } catch (Exception e) {
                System.err.println("Error closing B+ tree layer " + i + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public int getL() {
        return L;
    }

    public int getH() {
        return h;
    }

    public void print() {
        try {
            System.out.println("LSHF Index Contents:");
            for (int i = 0; i < L; i++) {
                System.out.println("Layer " + i + ":");
                BTFileScan scan = layers[i].new_scan(null, null); // Full scan
                KeyDataEntry entry;

                while ((entry = scan.get_next()) != null) {
                    Vector100DKey key = (Vector100DKey) entry.key;
                    RID rid = ((LeafData) entry.data).getData();
                    System.out.println("Key: " + key + ", RID: " + rid);
                }

                scan.DestroyBTreeFileScan();
            }
        } catch (Exception e) {
            System.err.println("Error printing LSHF index: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
