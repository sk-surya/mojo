package org.skor.jhighs.wrapper;

import org.skor.jhighs.bindings.highs_c_api_h;
import java.lang.foreign.*;
import org.skor.ArenaMemoryTracker;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


/**
 * Java wrapper for HiGHS C API providing an object-oriented interface.
 * This class manages a HiGHS instance and provides methods to build and solve optimization models.
 */
public class Highs implements AutoCloseable {
    
    static {
        // Load the HiGHS native library
        String libPath = loadLibraryPath();
        System.load(libPath);
    }
    
    private static String loadLibraryPath() {
        // First try system property
        String libPath = System.getProperty("highs.library.path");
        if (libPath != null && !libPath.isEmpty()) {
            return libPath;
        }
        
        // Try loading from config.properties
        try (InputStream input = Highs.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                Properties props = new Properties();
                props.load(input);
                libPath = props.getProperty("highs.library.path");
                if (libPath != null && !libPath.isEmpty()) {
                    return libPath;
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load config.properties: " + e.getMessage());
        }
        
        // Fall back to default path
        return "/usr/lib/libhighs.so";
    }
    
    private MemorySegment highsPtr;
    // private ArenaMemoryTracker arena;
    private Arena arena;
    private boolean disposed = false;
    
    /**
     * Creates a new HiGHS instance.
     */
    public Highs() {
        this.arena = Arena.ofConfined();
        // this.arena = new ArenaMemoryTracker(Arena.ofConfined());
        this.highsPtr = highs_c_api_h.Highs_create();
        if (highsPtr == null || highsPtr.address() == 0) {
            throw new RuntimeException("Failed to create HiGHS instance");
        }
    }
    
    /**
     * Run the solver on the current model.
     * @return Status code from HiGHS
     */
    public HighsStatus run() {
        checkNotDisposed();
        long status = highs_c_api_h.Highs_run(highsPtr);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Pass a complete model to HiGHS in compressed sparse column format.
     * 
     * @param numRow Number of constraints
     * @param numCol Number of variables
     * @param numNz Number of non-zeros in constraint matrix
     * @param aFormat Matrix format (1 for column-wise, 2 for row-wise)
     * @param sense Objective sense (1 for minimize, -1 for maximize)
     * @param offset Objective constant
     * @param colCost Objective coefficients
     * @param colLower Variable lower bounds
     * @param colUpper Variable upper bounds
     * @param rowLower Constraint lower bounds
     * @param rowUpper Constraint upper bounds
     * @param aStart Column/row start indices
     * @param aIndex Row/column indices of non-zeros
     * @param aValue Values of non-zeros
     * @param integrality Variable types (null for LP)
     * @return Status code
     */
    public HighsStatus passModel(
            int numRow, int numCol, int numNz, int aFormat,
            ObjSense sense, double offset,
            double[] colCost, double[] colLower, double[] colUpper,
            double[] rowLower, double[] rowUpper,
            int[] aStart, int[] aIndex, double[] aValue,
            HighsVarType[] integrality) {
        
        checkNotDisposed();
        
        // Allocate native memory for arrays
        MemorySegment colCostSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, colCost);
        MemorySegment colLowerSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, colLower);
        MemorySegment colUpperSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, colUpper);
        MemorySegment rowLowerSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, rowLower);
        MemorySegment rowUpperSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, rowUpper);
        MemorySegment aStartSeg = arena.allocateFrom(ValueLayout.JAVA_LONG, toLongArray(aStart));
        MemorySegment aIndexSeg = arena.allocateFrom(ValueLayout.JAVA_LONG, toLongArray(aIndex));
        MemorySegment aValueSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, aValue);
        
        long status;
        if (integrality == null) {
            // LP problem
            status = highs_c_api_h.Highs_passLp(
                highsPtr, (long)numCol, (long)numRow, (long)numNz, (long)aFormat,
                (long)sense.getValue(), offset,
                colCostSeg, colLowerSeg, colUpperSeg,
                rowLowerSeg, rowUpperSeg,
                aStartSeg, aIndexSeg, aValueSeg
            );
        } else {
            // MIP problem
            MemorySegment integralitySeg = arena.allocateFrom(
                ValueLayout.JAVA_LONG, 
                toIntegralityArray(integrality)
            );
            status = highs_c_api_h.Highs_passMip(
                highsPtr, (long)numCol, (long)numRow, (long)numNz, (long)aFormat,
                (long)sense.getValue(), offset,
                colCostSeg, colLowerSeg, colUpperSeg,
                rowLowerSeg, rowUpperSeg,
                aStartSeg, aIndexSeg, aValueSeg,
                integralitySeg
            );
        }
        
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Get the solution values for all columns.
     * @return Solution object containing primal values
     */
    public HighsSolution getSolution() {
        checkNotDisposed();
        
        int numCol = getNumCol();
        int numRow = getNumRow();
        
        MemorySegment colValueSeg = arena.allocate(ValueLayout.JAVA_DOUBLE, numCol);
        MemorySegment colDualSeg = arena.allocate(ValueLayout.JAVA_DOUBLE, numCol);
        MemorySegment rowValueSeg = arena.allocate(ValueLayout.JAVA_DOUBLE, numRow);
        MemorySegment rowDualSeg = arena.allocate(ValueLayout.JAVA_DOUBLE, numRow);
        
        highs_c_api_h.Highs_getSolution(
            highsPtr, colValueSeg, colDualSeg, rowValueSeg, rowDualSeg
        );
        
        double[] colValue = colValueSeg.toArray(ValueLayout.JAVA_DOUBLE);
        double[] colDual = colDualSeg.toArray(ValueLayout.JAVA_DOUBLE);
        double[] rowValue = rowValueSeg.toArray(ValueLayout.JAVA_DOUBLE);
        double[] rowDual = rowDualSeg.toArray(ValueLayout.JAVA_DOUBLE);
        
        return new HighsSolution(colValue, colDual, rowValue, rowDual);
    }
    
    /**
     * Get information about the solve.
     * @return HighsInfo object
     */
    public HighsInfo getInfo() {
        checkNotDisposed();
        
        double objValue = getDoubleInfoValue("objective_function_value");
        double mipGap = getDoubleInfoValue("mip_gap");
        long simplexIter = getInt64InfoValue("simplex_iteration_count");
        long mipNodeCount = getInt64InfoValue("mip_node_count");
        
        return new HighsInfo(objValue, mipGap, simplexIter, mipNodeCount);
    }
    
    /**
     * Get the model status after solving.
     * @return Model status enum
     */
    public HighsModelStatus getModelStatus() {
        checkNotDisposed();
        int status = (int) highs_c_api_h.Highs_getModelStatus(highsPtr);
        return HighsModelStatus.fromCode(status);
    }
    
    /**
     * Set a boolean option.
     */
    public HighsStatus setOptionValue(String option, boolean value) {
        checkNotDisposed();
        MemorySegment optionSeg = arena.allocateFrom(option);
        long status = highs_c_api_h.Highs_setBoolOptionValue(highsPtr, optionSeg, value ? 1 : 0);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Set an integer option.
     */
    public HighsStatus setOptionValue(String option, int value) {
        checkNotDisposed();
        MemorySegment optionSeg = arena.allocateFrom(option);
        long status = highs_c_api_h.Highs_setIntOptionValue(highsPtr, optionSeg, value);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Set a double option.
     */
    public HighsStatus setOptionValue(String option, double value) {
        checkNotDisposed();
        MemorySegment optionSeg = arena.allocateFrom(option);
        long status = highs_c_api_h.Highs_setDoubleOptionValue(highsPtr, optionSeg, value);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Set a string option.
     */
    public HighsStatus setOptionValue(String option, String value) {
        checkNotDisposed();
        MemorySegment optionSeg = arena.allocateFrom(option);
        MemorySegment valueSeg = arena.allocateFrom(value);
        long status = highs_c_api_h.Highs_setStringOptionValue(highsPtr, optionSeg, valueSeg);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Set a warm start solution.
     */
    public HighsStatus setSolution(double[] colValue) {
        checkNotDisposed();
        MemorySegment colValueSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, colValue);
        long status = highs_c_api_h.Highs_setSolution(highsPtr, colValueSeg, 
            MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Add a column (variable) to the model.
     */
    public HighsStatus addCol(double cost, double lower, double upper,
                               int numNz, int[] indices, double[] values) {
        checkNotDisposed();
        MemorySegment indicesSeg = arena.allocateFrom(ValueLayout.JAVA_LONG, toLongArray(indices));
        MemorySegment valuesSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, values);
        long status = highs_c_api_h.Highs_addCol(
            highsPtr, cost, lower, upper, numNz, indicesSeg, valuesSeg
        );
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Add a row (constraint) to the model.
     */
    public HighsStatus addRow(double lower, double upper,
                               int numNz, int[] indices, double[] values) {
        checkNotDisposed();
        MemorySegment indicesSeg = arena.allocateFrom(ValueLayout.JAVA_LONG, toLongArray(indices));
        MemorySegment valuesSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, values);
        long status = highs_c_api_h.Highs_addRow(
            highsPtr, lower, upper, numNz, indicesSeg, valuesSeg
        );
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Change variable bounds.
     */
    public HighsStatus changeColBounds(int col, double lower, double upper) {
        checkNotDisposed();
        long status = highs_c_api_h.Highs_changeColBounds(highsPtr, col, lower, upper);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Change constraint bounds.
     */
    public HighsStatus changeRowBounds(int row, double lower, double upper) {
        checkNotDisposed();
        long status = highs_c_api_h.Highs_changeRowBounds(highsPtr, row, lower, upper);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Change objective coefficient.
     */
    public HighsStatus changeColCost(int col, double cost) {
        checkNotDisposed();
        long status = highs_c_api_h.Highs_changeColCost(highsPtr, col, cost);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Change a single coefficient in the constraint matrix.
     */
    public HighsStatus changeCoeff(int row, int col, double value) {
        checkNotDisposed();
        long status = highs_c_api_h.Highs_changeCoeff(highsPtr, row, col, value);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Change the integrality of a column (variable).
     */
    public HighsStatus changeColIntegrality(int col, HighsVarType integrality) {
        checkNotDisposed();
        long status = highs_c_api_h.Highs_changeColIntegrality(highsPtr, (long)col, (long)integrality.getValue());
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Change the objective sense.
     */
    public HighsStatus changeObjectiveSense(ObjSense sense) {
        checkNotDisposed();
        long status = highs_c_api_h.Highs_changeObjectiveSense(highsPtr, (long)sense.getValue());
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Change the objective offset (constant).
     */
    public HighsStatus changeObjectiveOffset(double offset) {
        checkNotDisposed();
        long status = highs_c_api_h.Highs_changeObjectiveOffset(highsPtr, offset);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Delete columns by mask array.
     */
    public HighsStatus deleteColsByMask(boolean[] mask) {
        checkNotDisposed();
        // Convert boolean array to long array (HiGHS expects HighsInt array)
        long[] maskLong = new long[mask.length];
        for (int i = 0; i < mask.length; i++) {
            maskLong[i] = mask[i] ? 1L : 0L;
        }
        MemorySegment maskSeg = arena.allocateFrom(ValueLayout.JAVA_LONG, maskLong);
        long status = highs_c_api_h.Highs_deleteColsByMask(highsPtr, maskSeg);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Delete rows by mask array.
     */
    public HighsStatus deleteRowsByMask(boolean[] mask) {
        checkNotDisposed();
        // Convert boolean array to long array (HiGHS expects HighsInt array)
        long[] maskLong = new long[mask.length];
        for (int i = 0; i < mask.length; i++) {
            maskLong[i] = mask[i] ? 1L : 0L;
        }
        MemorySegment maskSeg = arena.allocateFrom(ValueLayout.JAVA_LONG, maskLong);
        long status = highs_c_api_h.Highs_deleteRowsByMask(highsPtr, maskSeg);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Write the model to a file.
     */
    public HighsStatus writeModel(String filename) {
        checkNotDisposed();
        MemorySegment filenameSeg = arena.allocateFrom(filename);
        long status = highs_c_api_h.Highs_writeModel(highsPtr, filenameSeg);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Clear the model.
     */
    public HighsStatus clearModel() {
        checkNotDisposed();
        long status = highs_c_api_h.Highs_clearModel(highsPtr);
        return HighsStatus.fromCode((int) status);
    }
    
    /**
     * Get number of columns.
     */
    public int getNumCol() {
        checkNotDisposed();
        return (int) highs_c_api_h.Highs_getNumCol(highsPtr);
    }
    
    /**
     * Get number of rows.
     */
    public int getNumRow() {
        checkNotDisposed();
        return (int) highs_c_api_h.Highs_getNumRow(highsPtr);
    }
    
    /**
     * Get a double info value.
     */
    private double getDoubleInfoValue(String info) {
        MemorySegment infoSeg = arena.allocateFrom(info);
        MemorySegment valueSeg = arena.allocate(ValueLayout.JAVA_DOUBLE);
        highs_c_api_h.Highs_getDoubleInfoValue(highsPtr, infoSeg, valueSeg);
        return valueSeg.get(ValueLayout.JAVA_DOUBLE, 0);
    }
    
    /**
     * Get an int64 info value.
     */
    private long getInt64InfoValue(String info) {
        MemorySegment infoSeg = arena.allocateFrom(info);
        MemorySegment valueSeg = arena.allocate(ValueLayout.JAVA_LONG);
        highs_c_api_h.Highs_getInt64InfoValue(highsPtr, infoSeg, valueSeg);
        return valueSeg.get(ValueLayout.JAVA_LONG, 0);
    }
    
    // Helper methods
    
    private long[] toLongArray(int[] arr) {
        long[] result = new long[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = arr[i];
        }
        return result;
    }
    
    private long[] toIntegralityArray(HighsVarType[] types) {
        long[] result = new long[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = types[i].getValue();
        }
        return result;
    }
    
    private void checkNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("HiGHS instance has been disposed");
        }
    }
    
    /**
     * Dispose of the HiGHS instance and free native resources.
     */
    public void dispose() {
        if (!disposed) {
            if (highsPtr != null) {
                highs_c_api_h.Highs_destroy(highsPtr);
                highsPtr = null;
            }
            if (arena != null) {
				// print arena memory tracker stats before closing
				if (arena instanceof ArenaMemoryTracker)
					((ArenaMemoryTracker) arena).printStats();
                arena.close();
                arena = null;
            }
            disposed = true;
        }
    }
    
    @Override
    public void close() {
        dispose();
    }
    
    // @Override
    // protected void finalize() throws Throwable {
    //     try {
    //         dispose();
    //     } finally {
    //         super.finalize();
    //     }
    // }
}

