import btree.BTFileScan;
import btree.KeyDataEntry;
import btree.LeafData;
import diskmgr.*;
import global.*;
import heap.*;
import java.io.*;
import java.util.*;
import lshfindex.*;

public class batchinsert {
    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: batchinsert h L DATAFILENAME DBNAME");
            System.exit(1);
        }

        try {
            // parse command-line arguments
            int h = Integer.parseInt(args[0]);
            int L = Integer.parseInt(args[1]);
            String dataFileName = args[2];
            String dbName = args[3];

            // open the data file
            BufferedReader br = new BufferedReader(new FileReader(dataFileName));

            // read number of attributes
            String line = br.readLine();
            if (line == null) {
                System.err.println("Data file is empty.");
                System.exit(1);
            }
            int numAttrs = Integer.parseInt(line.trim());

            // read attribute types
            line = br.readLine();
            if (line == null) {
                System.err.println("Missing attribute types line.");
                System.exit(1);
            }
            String[] typeTokens = line.trim().split("\\s+");
            if (typeTokens.length != numAttrs) {
                System.err.println("Number of attribute types does not match number of attributes.");
                System.exit(1);
            }

            // create arrays for attribute types and string sizes
            AttrType[] attrTypes = new AttrType[numAttrs];
            short[] strSizes = new short[numAttrs];
            for (int i = 0; i < numAttrs; i++) {
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

            // create heapfile with the DBNAME
            // initialize the database
            int numPages = 100 * GlobalConst.NUMBUF; // Total number of pages
            int numBufs = 100 * GlobalConst.NUMBUF; // Number of buffer pages
            String replacementPolicy = "Clock";
            new SystemDefs(dbName, numPages, numBufs, replacementPolicy);

            Heapfile hf = new Heapfile(dbName);

            // create a single LSHFIndexFile for all vector attributes
            LSHFIndexFile lshfIndex = new LSHFIndexFile(dbName, h, L);

            // insert tuples into the heapfile and LSHF index
            int tupleCount = 0;
            Tuple tuple = new Tuple();
            while ((line = br.readLine()) != null) {
                // read `numAttrs` lines for the current tuple
                String[] tupleValues = new String[numAttrs];
                tupleValues[0] = line.trim(); // first attribute value
                boolean incompleteTuple = false;

                for (int i = 1; i < numAttrs; i++) {
                    line = br.readLine();
                    if (line == null) {
                        System.err.println("Warning: Incomplete tuple in input file. Skipping this tuple.");
                        incompleteTuple = true;
                        break;
                    }
                    tupleValues[i] = line.trim();
                }

                // skip incomplete tuples
                if (incompleteTuple) {
                    continue;
                }

                // populate the tuple
                tuple.setHdr((short) numAttrs, attrTypes, strSizes);
                for (int i = 0; i < numAttrs; i++) {
                    switch (attrTypes[i].attrType) {
                        case AttrType.attrInteger:
                            tuple.setIntFld(i + 1, Integer.parseInt(tupleValues[i]));
                            break;
                        case AttrType.attrReal:
                            tuple.setFloFld(i + 1, Float.parseFloat(tupleValues[i]));
                            break;
                        case AttrType.attrString:
                            tuple.setStrFld(i + 1, tupleValues[i]);
                            break;
                        case AttrType.attrVector100D:
                            String[] vectorTokens = tupleValues[i].split("\\s+");
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

                // Insert the tuple into the heapfile and get its RID
                RID rid = hf.insertRecord(tuple.getTupleByteArray());

                // Insert the vector into the LSHF index with the RID
                for (int i = 0; i < numAttrs; i++) {
                    if (attrTypes[i].attrType == AttrType.attrVector100D) {
                        String[] vectorTokens = tupleValues[i].split("\\s+");
                        short[] vector = new short[vectorTokens.length];
                        for (int j = 0; j < vectorTokens.length; j++) {
                            double decimalValue = Double.parseDouble(vectorTokens[j]);
                            vector[j] = (short) Math.round(decimalValue);
                        }
                        Vector100Dtype vector100D = new Vector100Dtype(vector);
                        lshfIndex.insert(vector100D, rid); // Pass the RID to the LSHFIndexFile
                    }
                }

                tupleCount++;
            }
            br.close();

            // store to DB
            // After inserting all vectors into the LSHFIndexFile
            try {
                // Step 1: Allocate a page for the LSHFIndexFile metadata
                PageId lshfIndexPageId = new PageId();
                SystemDefs.JavabaseDB.allocate_page(lshfIndexPageId);

                // Step 2: Serialize the LSHFIndexFile metadata
                Page lshfIndexPage = new Page();
                byte[] pageData = lshfIndexPage.getpage();
                String metadata = "h=" + h + "\nL=" + L + "\n";
                // Ensure the rest of the page buffer is cleared
                Arrays.fill(pageData, metadata.length(), pageData.length, (byte) 0);
                System.arraycopy(metadata.getBytes(), 0, pageData, 0, metadata.length());

                // Step 3: Write the metadata to the allocated page
                SystemDefs.JavabaseDB.write_page(lshfIndexPageId, lshfIndexPage);

                // Step 4: Add a file entry for the LSHFIndexFile in the database
                System.out.println("Adding file entry for LSHFIndexFile: " + dbName + "_lshfindex, PageId: "
                        + lshfIndexPageId.pid);
                SystemDefs.JavabaseDB.add_file_entry(dbName, lshfIndexPageId);

                // Step 5: Save each B+ tree layer of the LSHFIndexFile
                for (int i = 0; i < L; i++) {
                    String layerFileName = dbName + "_layer" + i;
                    PageId layerPageId = new PageId();
                    SystemDefs.JavabaseDB.allocate_page(layerPageId);

                    // Serialize the layer metadata (e.g., file name)
                    Page layerPage = new Page();
                    byte[] layerPageData = layerPage.getpage();
                    System.arraycopy(layerFileName.getBytes(), 0, layerPageData, 0, layerFileName.length());

                    // Write the layer metadata to the allocated page
                    SystemDefs.JavabaseDB.write_page(layerPageId, layerPage);

                    // Add a file entry for the layer
                    SystemDefs.JavabaseDB.add_file_entry(layerFileName, layerPageId);
                }

                System.out.println("LSHFIndexFile structure saved to the database.");
            } catch (Exception e) {
                System.err.println("Error saving LSHFIndexFile to the database: " + e.getMessage());
                e.printStackTrace();
            }

            // close the LSHF index
            // lshfIndex.print();
            lshfIndex.close();

            // flush all pages
            SystemDefs.JavabaseBM.flushAllPages();

            // print number of disk pages read and written
            System.out.println("Number of disk pages read: " + PCounter.rcounter);
            System.out.println("Number of disk pages written: " + PCounter.wcounter);
            System.out.println("Inserted " + tupleCount + " tuples.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
