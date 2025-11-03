package org.skor.jhighs.wrapper;

/**
 * Solution data from HiGHS solve.
 */
public class HighsSolution {
    private final double[] colValue;
    private final double[] colDual;
    private final double[] rowValue;
    private final double[] rowDual;
    
    public HighsSolution(double[] colValue, double[] colDual, 
                         double[] rowValue, double[] rowDual) {
        this.colValue = colValue;
        this.colDual = colDual;
        this.rowValue = rowValue;
        this.rowDual = rowDual;
    }
    
    public double[] getColValue() {
        return colValue;
    }
    
    public double[] getColDual() {
        return colDual;
    }
    
    public double[] getRowValue() {
        return rowValue;
    }
    
    public double[] getRowDual() {
        return rowDual;
    }
}
