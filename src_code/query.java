import java.io.*;
import java.util.*;

import diskmgr.*;
import global.*;
import heap.*;
import lshfindex.*;

/**
 * Query program for MiniBase.
 * Usage: java query DBNAME QSNAME INDEXOPTION NUMBUF
 */
public class query {
    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: query DBNAME QSNAME INDEXOPTION NUMBUF");
            System.exit(1);
        }

        // Parse command-line arguments
        String dbName = args[0];
        String qsName = args[1];
        String indexOption = args[2];
        int numBuf = Integer.parseInt(args[3]);

        // Initialize MiniBase
        int numPages = 10 * GlobalConst.NUMBUF;
        new SystemDefs(dbName, numPages, numBuf, "Clock");

        // Read the query specification
        String querySpec = null;
        try (BufferedReader br = new BufferedReader(new FileReader(qsName))) {
            querySpec = br.readLine();
        } catch (Exception e) {
            System.err.println("Error reading query spec file: " + e.getMessage());
            System.exit(1);
        }
        if (querySpec == null) {
            System.err.println("Query spec file is empty.");
            System.exit(1);
        }

        // Parse the query type and parameters
        boolean isRange = false, isNN = false;
        if (querySpec.startsWith("Range(")) {
            isRange = true;
        } else if (querySpec.startsWith("NN(")) {
            isNN = true;
        } else {
            System.err.println("Unknown query type: " + querySpec);
            System.exit(1);
        }

        // Extract query parameters
        int startIdx = querySpec.indexOf('(');
        int endIdx = querySpec.lastIndexOf(')');
        if (startIdx < 0 || endIdx < 0 || endIdx <= startIdx) {
            System.err.println("Malformed query specification.");
            System.exit(1);
        }

        String paramStr = querySpec.substring(startIdx + 1, endIdx);
        String[] params = paramStr.split(",");
        for (int i = 0; i < params.length; i++) {
            params[i] = params[i].trim();
        }

        if (params.length < 3) {
            System.err.println("Query specification must have at least 3 parameters.");
            System.exit(1);
        }

        // Parse parameters
        int qa = Integer.parseInt(params[0]); // Query field number
        String targetFile = params[1]; // Target vector file
        int thresholdOrK = Integer.parseInt(params[2]); // Range or K
        String[] outputFields = Arrays.copyOfRange(params, 3, params.length);

        // Read the target vector
        Vector100Dtype targetVector = readTargetVector(targetFile);

        // Open the heapfile
        Heapfile hf = null;
        try {
            hf = new Heapfile(dbName);
        } catch (Exception e) {
            System.err.println("Error opening heapfile: " + e.getMessage());
            System.exit(1);
        }

        // Read metadata from the file
        int h = 0, L = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(dbName + "_meta"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("h=")) {
                    h = Integer.parseInt(line.substring(2));
                } else if (line.startsWith("L=")) {
                    L = Integer.parseInt(line.substring(2));
                }
            }
            if (h <= 0 || L <= 0) {
                throw new IllegalStateException("Invalid metadata for LSHFIndexFile.");
            }
            System.out.println("Metadata loaded: h=" + h + ", L=" + L);
        } catch (IOException e) {
            System.err.println("Error reading metadata: " + e.getMessage());
            System.exit(1);
        }

        // Reopen the LSHFIndexFile using the metadata
        LSHFIndexFile lshfIndex = null;
        if (indexOption.equalsIgnoreCase("Y")) {
            try {
                // Get the starting page ID of the LSHFIndexFile
                System.out.println("Retrieving file entry for LSHFIndexFile: " + dbName);
                PageId lshfIndexPageId = SystemDefs.JavabaseDB.get_file_entry(dbName);
                if (lshfIndexPageId == null) {
                    throw new IOException("File entry for LSHFIndexFile not found: " + dbName + "_lshfindex");
                }
                System.out.println("Retrieved PageId: " + lshfIndexPageId.pid);

                // Read the metadata from the starting page
                Page lshfIndexPage = new Page();
                SystemDefs.JavabaseDB.read_page(lshfIndexPageId, lshfIndexPage);
                byte[] pageData = lshfIndexPage.getpage();
                String metadata = new String(pageData).trim(); // Convert byte array to string and trim whitespace
                System.out.println("Raw metadata: " + metadata); // Debugging

                String[] lines = metadata.split("\n"); // Split metadata into lines
                System.out.println("Parsed lines: " + Arrays.toString(lines)); // Debugging

                if (lines.length < 2) {
                    throw new IOException(
                            "Invalid metadata format. Expected at least 2 lines, but got: " + lines.length);
                }

                h = Integer.parseInt(lines[0].split("=")[1]); // Parse h value
                L = Integer.parseInt(lines[1].split("=")[1]); // Parse L value
                lshfIndex = new LSHFIndexFile(dbName, h, L);
                System.out.println("LSHFIndexFile structure loaded from the database.");
            } catch (Exception e) {
                System.err.println("Error loading LSHFIndexFile from the database: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }

        // Perform the query
        int count = 0;
        if (indexOption.equalsIgnoreCase("Y") && lshfIndex != null) {
            try {
                if (isRange) {
                    // Perform range query using LSHFIndexFile
                    Vector<RID> results = lshfIndex.LSHFFileRangeScan(new Vector100DKey(targetVector, 0), thresholdOrK);
                    count = processResults(hf, results, outputFields);
                } else if (isNN) {
                    // Perform nearest neighbor query using LSHFIndexFile
                    Vector<RID> results = lshfIndex.LSHFFileNNScan(new Vector100DKey(targetVector, 0), thresholdOrK);
                    count = processResults(hf, results, outputFields);
                }
            } catch (Exception e) {
                System.err.println("Error performing index query: " + e.getMessage());
                System.exit(1);
            }
        } else {
            // Perform full heapfile scan
            try {
                count = fullHeapfileScan(hf, targetVector, thresholdOrK, isRange, outputFields, qa);
            } catch (Exception e) {
                System.err.println("Error performing full heapfile scan: " + e.getMessage());
                System.exit(1);
            }
        }

        // Output query results
        System.out.println("Query Results:");
        System.out.println("Total tuples returned: " + count);
        System.out.println("Disk pages read: " + PCounter.rcounter);
        System.out.println("Disk pages written: " + PCounter.wcounter);

        // Close the LSHFIndexFile
        if (lshfIndex != null) {
            lshfIndex.close();
        }
    }

    private static Vector100Dtype readTargetVector(String targetFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(targetFile))) {
            String line = br.readLine();
            if (line == null) {
                throw new IOException("Target vector file is empty.");
            }

            String[] tokens = line.trim().split("\\s+");
            if (tokens.length != Vector100Dtype.SIZE) {
                throw new IOException(
                        "Expected " + Vector100Dtype.SIZE + " integers in target vector, but got " + tokens.length);
            }

            short[] vecArray = new short[Vector100Dtype.SIZE];
            for (int i = 0; i < Vector100Dtype.SIZE; i++) {
                vecArray[i] = Short.parseShort(tokens[i]);
            }

            return new Vector100Dtype(vecArray);
        } catch (Exception e) {
            System.err.println("Error reading target vector: " + e.getMessage());
            System.exit(1);
            return null; // Unreachable
        }
    }

    private static int processResults(Heapfile hf, Vector<RID> results, String[] outputFields) throws Exception {
        int count = 0;
        for (RID rid : results) {
            Tuple tuple = hf.getRecord(rid); // Retrieve the tuple using the RID
            tuple.print(getSchemaForTuple()); // Print the tuple or process it as needed
            count++;
        }
        return count;
    }

    private static int fullHeapfileScan(Heapfile hf, Vector100Dtype targetVector, int thresholdOrK, boolean isRange,
            String[] outputFields, int qa) throws Exception {
        int count = 0;
        Scan scan = hf.openScan();
        RID rid = new RID();
        Tuple tuple = new Tuple();

        while ((tuple = scan.getNext(rid)) != null) {
            tuple.setHdr((short) getSchemaForTuple().length, getSchemaForTuple(), null);

            // Retrieve the vector field
            Vector100Dtype vector = tuple.get100DVectFld(qa);

            if (isRange) {
                // Check if the vector is within the range
                if (vector.computeDistance(targetVector) <= thresholdOrK) {
                    tuple.print(getSchemaForTuple());
                    count++;
                }
            } else {
                // Nearest neighbor logic (if K=0, return all sorted by distance)
                // Placeholder: Implement nearest neighbor logic here
            }
        }

        scan.closescan();
        return count;
    }

    private static AttrType[] getSchemaForTuple() {
        // Define the schema for the tuples
        return new AttrType[] {
                new AttrType(AttrType.attrInteger),
                new AttrType(AttrType.attrVector100D),
                new AttrType(AttrType.attrReal),
                new AttrType(AttrType.attrVector100D)
        };
    }
}