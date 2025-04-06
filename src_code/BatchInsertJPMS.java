
import diskmgr.*;
import global.*;
import heap.*;
import lshfindex.LSHFIndexFile;
import solution.SolutionHelper;

import java.io.*;
import java.security.InvalidParameterException;
import java.util.*;

public class BatchInsertJPMS {
    public static void main(String[] args) throws Exception {

        registerLogstreamFileAndRedirectLogOutput();

        try {

            CommandInput input = CommandInput.parseFromArgs(args);

            new SystemDefs(input.databaseName, 10 * GlobalConst.NUMBUF, 10 * GlobalConst.NUMBUF, "Clock");

            Heapfile hf = new Heapfile(input.databaseName);

            LSHFIndexFile lshfIndex = new LSHFIndexFile(input.databaseName, input.lshHashPerLayerCount, input.lshLayerCount);

            ImportingDataInformation importingData = getDataFromSourceFile(input.fileContainsDataFilePath);
            // insert tuples into the heapfile and LSHF index
            for (Tuple tuple : importingData.tuples) {

                RID rid = hf.insertRecord(tuple.getTupleByteArray());

                Integer numAttrs = importingData.metadata.attrTypes.length;
                // Insert the vector into the LSHF index with the RID
                for (int i = 0; i < numAttrs; i++) {
                    if (importingData.metadata.attrTypes[i].attrType == AttrType.attrVector100D) {
                        Vector100Dtype key = tuple.get100DVectFld(i);
                        lshfIndex.insert(key, rid);
                    }
                }
            }

            // store to DB
            // After inserting all vectors into the LSHFIndexFile
  

            // close the LSHF index            lshfIndex.close();

            // flush all pages
            // SystemDefs.JavabaseBM.flushAllPages();

            // print number of disk pages read and written
            System.out.println("Number of disk pages read: " + PCounter.rcounter);
            System.out.println("Number of disk pages written: " + PCounter.wcounter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static TupleMetadata getTupleMetadataFromColumnDeclarations(String[] typeTokens) {
        AttrType[] attrTypes = new AttrType[typeTokens.length];
        short[] strSizes = new short[typeTokens.length];

        for (int i = 0; i < typeTokens.length; i++) {
            int t = Integer.parseInt(typeTokens[i]);
            switch (t) {
                case 1:
                    attrTypes[i] = new AttrType(AttrType.attrInteger);
                    break;
                case 2:
                    attrTypes[i] = new AttrType(AttrType.attrReal);
                    break;
                case 3:
                    attrTypes[i] = new AttrType(AttrType.attrString);
                    // Default string size
                    strSizes[i] = 30;
                    break;
                case 4:
                    attrTypes[i] = new AttrType(AttrType.attrVector100D);
                    break;
                default:
                    System.err.println("Unknown attribute type: " + t);
                    System.exit(1);
            }
        }

        return new TupleMetadata(attrTypes, strSizes);
    }

    private static void registerLogstreamFileAndRedirectLogOutput() {
        try {
            FileOutputStream logFile = new FileOutputStream("logfile.txt"); // Append mode
            PrintStream logStream = new PrintStream(logFile);
            PrintStream infoStream = new PrintStream(new PrefixPrintStream(logStream, "[INFO] "));
            PrintStream errorStream = new PrintStream(new PrefixPrintStream(logStream, "[ERROR] "));
            System.setOut(infoStream);
            System.setErr(errorStream);

        } catch (FileNotFoundException e) {
            // for debug purpose
            e.printStackTrace();
        }
    }

    private static Tuple parseTupleData(String[] tupleColumnValues, TupleMetadata tupleMetadata) throws Exception {
        Tuple tuple = new Tuple();

        tuple.setHdr((short) tupleMetadata.attrTypes.length, tupleMetadata.attrTypes, tupleMetadata.strSizes);

        for (int i = 0; i < tupleColumnValues.length; i++) {
            switch (tupleMetadata.attrTypes[i].attrType) {
                case AttrType.attrInteger:
                    tuple.setIntFld(i + 1, Integer.parseInt(tupleColumnValues[i]));
                    break;
                case AttrType.attrReal:
                    tuple.setFloFld(i + 1, Float.parseFloat(tupleColumnValues[i]));
                    break;
                case AttrType.attrString:
                    tuple.setStrFld(i + 1, tupleColumnValues[i]);
                    break;
                case AttrType.attrVector100D:
                    String[] vectorTokens = tupleColumnValues[i].split("\\s+");
                    short[] vector = new short[vectorTokens.length];
                    for (int j = 0; j < vectorTokens.length; j++) {
                        // Parse the decimal value and round it to the nearest integer
                        double decimalValue = Double.parseDouble(vectorTokens[j]);
                        vector[j] = (short) Math.round(decimalValue);
                    }
                    tuple.set100DVectFld(i + 1, new Vector100Dtype(vector));

                    break;
                default:
                    throw new IllegalArgumentException("Unknown attribute type.");
            }
        }

        return tuple;
    }

    private static ImportingDataInformation getDataFromSourceFile(String fileName) throws Exception {
        
        BufferedReader br = new BufferedReader(new FileReader(fileName));

        // read number of attributes
        String line = br.readLine();
        if (line == null) {
            System.err.println("Data file is empty.");
            System.exit(1);
        }
        int numAttrs = Integer.parseInt(line.trim());

        // read columns type definition
        line = br.readLine();
        if (line == null) {
            System.err.println("Missing attribute types line.");
            System.exit(1);
        }

        // split by space
        String[] typeTokens = line.trim().split("\\s+");
        if (typeTokens.length != numAttrs) {
            System.err.println("Number of attribute types does not match number of attributes.");
            System.exit(1);
        }

        TupleMetadata metadata = getTupleMetadataFromColumnDeclarations(typeTokens);

        ArrayList<Tuple> tuples = new ArrayList<Tuple>();
        Boolean shouldContinue = true;
        do {
            String[] tupleColumnValues = new String[numAttrs];

            for (int i = 0; i < numAttrs; i++) {
                line = br.readLine();
                if (line == null) {
                    shouldContinue = false;
                    break;
                }
                tupleColumnValues[i] = line.trim();
            }
            if (!shouldContinue) {
                break;
            }
            
            tuples.add(parseTupleData(tupleColumnValues, metadata));
        
        } while (shouldContinue);
        System.out.println("Found a total of " + tuples.toArray().length);
        return new ImportingDataInformation(metadata, tuples.toArray(new Tuple[tuples.toArray().length]));
    }
}

class ImportingDataInformation {
    public final TupleMetadata metadata;
    public final Tuple[] tuples;

    public ImportingDataInformation(TupleMetadata metadata, Tuple[] tuples) {
        this.metadata = metadata;
        this.tuples = tuples;
    }
}

class TupleMetadata { // might wanna make this something within the codebase to be exposed to client ?
                      // considerations :v
    public final AttrType[] attrTypes;
    public final short[] strSizes;

    // Constructor
    public TupleMetadata(AttrType[] attrTypes, short[] strSizes) {
        this.attrTypes = attrTypes;
        this.strSizes = strSizes;
    }
}

// Custom PrintStream that adds a prefix to each message
class PrefixPrintStream extends PrintStream {
    private final String prefix;

    // Constructor to set the prefix
    public PrefixPrintStream(PrintStream out, String prefix) {
        super(out);
        this.prefix = prefix;
    }

    // Override the println method to add the prefix
    @Override
    public void println(String message) {
        super.println(prefix + message);
    }

    // Optional: Override other print methods if needed
    @Override
    public void print(String message) {
        super.print(prefix + message);
    }
}


class CommandInput {
    public final String databaseName;
    public final String fileContainsDataFilePath;
    public final Integer lshLayerCount;
    public final Integer lshHashPerLayerCount;

    public CommandInput(String dbname, String path, Integer layer, Integer hashPerLayer) {
        this.databaseName = dbname;
        this.fileContainsDataFilePath = path;
        this.lshLayerCount = layer;
        this.lshHashPerLayerCount = hashPerLayer;
    }

    public static CommandInput parseFromArgs(String[] args) throws InvalidParameterException {
        if (args.length != 4) {
            throw new InvalidParameterException("Parameters should be 4");
        }

        int hashes = Integer.parseInt(args[0]);
        int layers = Integer.parseInt(args[1]);
        String dataFileName = args[2];
        String dbName = args[3];

        return new CommandInput(dbName, dataFileName, layers, hashes);
    }
}