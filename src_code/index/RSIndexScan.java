package index;

import global.*;
import bufmgr.*;
import diskmgr.*;
import lshfindex.*;
import iterator.*;
import heap.*;
import java.io.*;
import java.util.Vector;

import btree.*;

public class RSIndexScan extends Iterator {
    // Class fields
    public FldSpec[] perm_mat;
    private IndexFile indFile;
    private AttrType[] _types;
    private short[] _s_sizes;
    private CondExpr[] _selects;
    private int _noInFlds;
    private int _noOutFlds;
    private Heapfile hf;
    private Tuple tuple1;
    private Tuple Jtuple;
    private int t1_size;
    private int _fldNum;

    private LSHFIndexFile lshfIndexFile;
    private Vector<RID> rid_results;

    /**
     * Class constructor: set up the RS index scan
     * 
     * @param index
     *            type of the index (must be IndexType.LSHFIndex)
     * @param relName
     *            name of the input relation (heapfile name)
     * @param indName
     *            name of the input index (LSHF index file name)
     * @param types
     *            array of types in this relation
     * @param str_sizes
     *            array of string sizes (for attributes that are string)
     * @param noInFlds
     *            number of fields in input tuple
     * @param noOutFlds
     *            number of fields in output tuple
     * @param outFlds
     *            fields to project
     * @param selects
     *            selection conditions to apply (first one is primary)
     * @param fldNum
     *            field number of the indexed field
     * @param query
     *            the query vector (of type Vector100Dtype)
     * @param distance
     *            non-negative integer indicating the range (distance) for the scan
     * @exception IndexException
     *                if there is an error from the lower layers
     * @exception InvalidTypeException
     *                if the tuple type is not valid
     * @exception InvalidTupleSizeException
     *                if the tuple size is not valid
     * @exception UnknownIndexTypeException
     *                if the index type is unknown or not supported
     * @exception IOException
     *                if an I/O error occurs
     */
    public RSIndexScan(IndexType index,
            final String relName,
            final String indName,
            AttrType types[],
            short str_sizes[],
            int noInFlds,
            int noOutFlds,
            FldSpec outFlds[],
            CondExpr selects[],
            final int fldNum,
            Vector100Dtype query,
            int distance)
            throws IndexException, InvalidTypeException, InvalidTupleSizeException,
            UnknownIndexTypeException, IOException {
        _fldNum = fldNum;
        _noInFlds = noInFlds;
        _types = types;
        _s_sizes = str_sizes;

        // setup output tuple header
        AttrType[] Jtypes = new AttrType[noOutFlds];
        short[] ts_sizes;

        // Set up the output tuple types based on the input types and the fields to
        // project
        Jtuple = new Tuple();
        try {
            ts_sizes = TupleUtils.setup_op_tuple(Jtuple, Jtypes, types, noInFlds, str_sizes, outFlds, noOutFlds);
        } catch (TupleUtilsException e) {
            throw new IndexException(e,
                    "RSIndexScan.java: TupleUtilsException caught from TupleUtils.setup_op_tuple()");
        } catch (InvalidRelation e) {
            throw new IndexException(e, "RSIndexScan.java: InvalidRelation caught from TupleUtils.setup_op_tuple()");
        }

        _selects = selects;
        perm_mat = outFlds;
        _noOutFlds = noOutFlds;

        // Set up the tuple for the input relation
        // This tuple will be used to retrieve the full tuple from the heapfile
        tuple1 = new Tuple();
        try {
            tuple1.setHdr((short) noInFlds, types, str_sizes);
        } catch (Exception e) {
            throw new IndexException(e, "RSIndexScan.java: Heapfile error during header setup");
        }

        t1_size = tuple1.size();

        try {
            hf = new Heapfile(relName);
        } catch (Exception e) {
            throw new IndexException(e, "RSIndexScan.java: Heapfile not created");
        }

        // only support LSHFIndex for RSIndexScan
        switch (index.indexType) {
            case IndexType.LSHFIndex:
                try {
                    lshfIndexFile = new LSHFIndexFile(indName); // Create LSHF index file
                } catch (Exception e) {
                    throw new IndexException(e, "RSIndexScan.java: Unable to create LSHFIndexFile");
                }

                KeyClass key = new Vector100DKey(query);
                rid_results = lshfIndexFile.LSHFFileRangeScan(key, distance);
                break;
            default:
                throw new UnknownIndexTypeException("RSIndexScan: Only LSHFIndex is supported in RSIndexScan");
        }
    }

    /**
     * returns the next tuple.
     * if <code>index_only</code>, only returns the key value
     * (as the first field in a tuple)
     * otherwise, retrive the tuple and returns the whole tuple
     * 
     * @return the tuple
     * @exception IndexException
     *                error from the lower layer
     * @exception UnknownKeyTypeException
     *                key type unknown
     * @exception IOException
     *                from the lower layer
     */
    public Tuple get_next() throws IndexException, UnknownKeyTypeException, IOException {
        RID rid;

        for (int i = 0; i < rid_results.size(); i++) {
            rid = rid_results.get(i); // Get the RID from the list of RIDs returned by the LSHF index scan
            // Get the record from the heapfile using the RID
            try {
                tuple1 = hf.getRecord(rid);
            } catch (Exception e) {
                throw new IndexException(e, "IndexScan.java: getRecord failed");
            }

            try {
                tuple1.setHdr((short) _noInFlds, _types, _s_sizes);
            } catch (Exception e) {
                throw new IndexException(e, "IndexScan.java: Heapfile error");
            }

            boolean eval;
            try {
                eval = PredEval.Eval(_selects, tuple1, null, _types, null);
            } catch (Exception e) {
                throw new IndexException(e, "IndexScan.java: Heapfile error");
            }

            if (eval) {
                // need projection.java
                try {
                    Projection.Project(tuple1, _types, Jtuple, perm_mat, _noOutFlds);
                } catch (Exception e) {
                    throw new IndexException(e, "IndexScan.java: Heapfile error");
                }

                return Jtuple;
            }
        }

        return null;
    }

    /**
     * Cleans up the index scan
     */
    public void close() {
        lshfIndexFile.close(); // Close the LSHF index file
    }
}
