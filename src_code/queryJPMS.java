import java.io.*;
import java.security.InvalidParameterException;
import java.util.*;

import javax.management.Query;

import diskmgr.*;
import global.*;
import heap.*;
import lshfindex.*;

/**
 * Query program for MiniBase.
 * Usage: java query DBNAME QSNAME INDEXOPTION NUMBUF
 */
public class queryJPMS {
    public static void main(String[] args) {

        CommandInput input = CommandInput.parseFromArgs(args);

        String queryString = null;
        try (BufferedReader br = new BufferedReader(new FileReader(input.querySourceFile))) {
            queryString = br.readLine();
        }

        if (queryString == null) {
            throw new Exception("Empty Query");
        }

        CustomQuery query = CustomQuery.parseFromQueryString(queryString);

        int numPages = 10 * GlobalConst.NUMBUF;
        new SystemDefs(input.databaseName, numPages, input.numberOfBuffer, "Clock");

        Heapfile hf = new Heapfile(input.databaseName);

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

    // private static

}

class CommandInput {
    public final String databaseName;
    public final String querySourceFile;
    public final Boolean isUsingIndex;
    public final Integer numberOfBuffer;

    public CommandInput(String dbname, String querySource, Boolean usingIndex, Integer numBuf) {
        this.databaseName = dbname;
        this.querySourceFile = querySource;
        this.isUsingIndex = usingIndex;
        this.numberOfBuffer = numBuf;
    }

    public static CommandInput parseFromArgs(String[] args) throws InvalidParameterException {
        if (args.length != 4) {
            throw new InvalidParameterException("Parameters should be 4");
        }

        // Parse command-line arguments
        String dbName = args[0];
        String qsName = args[1];
        Boolean indexOption = args[2].equalsIgnoreCase("Y") || false;
        int numBuf = Integer.parseInt(args[3]);

        return new CommandInput(dbName, qsName, indexOption, numBuf);
    }
}

class CustomQuery {
    public final QueryType type;
    public final Integer targetColumnNumber;
    public final Integer nonNegativeRangeOrDistance;
    public final short[] targetVectorValues;
    public final Integer[] outputFields;

    public CustomQuery(QueryType type, Integer targetColumnNumber, Integer nonNegativeRangeOrDistance,
            short[] targetVectorValues, Integer[] outputFields) {
        this.type = type;
        this.targetColumnNumber = targetColumnNumber;
        this.nonNegativeRangeOrDistance = nonNegativeRangeOrDistance;
        this.targetVectorValues = targetVectorValues;
        this.outputFields = outputFields;
    }

    public static CustomQuery parseFromQueryString(String query) throws Exception, InvalidParameterException {

        // can improve this to accommodate nested queries ? eh too dreamy
        int firstOpeningParentheses = query.indexOf('(');
        String queryTypeString = query.substring(0, firstOpeningParentheses);

        QueryType queryType;
        switch (queryTypeString) {
            case "Range":
                queryType = QueryType.RANGE;
                break;
            case "NN":
                queryType = QueryType.KNN;
                break;
            default:
                throw new InvalidParameterException("Unsupported query type");
        }

        String[] queryFunctionParameters = query.substring(firstOpeningParentheses, query.length() - 1).split(",");

        Integer targetColumnNumber = Integer.parseInt(queryFunctionParameters[0]);

        String filePath = queryFunctionParameters[1];
        short[] vectorValues = getVectorFromFile(filePath);

        Integer nonNegativeRangeOrDistance = Integer.parseInt(queryFunctionParameters[2]);

        Integer[] outputFields = new Integer[queryFunctionParameters.length - 3];
        for (int i = 3; i < queryFunctionParameters.length; i++) {
            outputFields[i - 3] = Integer.parseInt(queryFunctionParameters[i]);
        }

        return new CustomQuery(queryType, targetColumnNumber, nonNegativeRangeOrDistance, vectorValues, outputFields);
    }

    public static short[] getVectorFromFile(String filePath) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine();
            if (line == null) {
                throw new IOException("File Is Empty");
            }

            String[] tokens = line.trim().split("\\s+");

            if (tokens.length != Vector100Dtype.SIZE) {
                throw new IOException(
                        "Expected " + Vector100Dtype.SIZE + " integers in target vector, but got " + tokens.length);
            }

            short[] vectorValues = new short[Vector100Dtype.SIZE];
            for (int i = 0; i < Vector100Dtype.SIZE; i++) {
                vectorValues[i] = Short.parseShort(tokens[i]);
            }

            return vectorValues;
        }
    }
}

enum QueryType {
    RANGE, KNN,
}
