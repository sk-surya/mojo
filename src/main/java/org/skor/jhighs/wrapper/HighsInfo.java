package org.skor.jhighs.wrapper;

/**
 * Information about the solve from HiGHS.
 */
public class HighsInfo {
    public final double objective_function_value;
    public final double mip_gap;
    public final long simplex_iteration_count;
    public final long mip_node_count;
    
    public HighsInfo(double objective_function_value, double mip_gap,
                     long simplex_iteration_count, long mip_node_count) {
        this.objective_function_value = objective_function_value;
        this.mip_gap = mip_gap;
        this.simplex_iteration_count = simplex_iteration_count;
        this.mip_node_count = mip_node_count;
    }
    
    public double getObjectiveValue() {
        return objective_function_value;
    }
    
    public double getMipGap() {
        return mip_gap;
    }
    
    public long getSimplexIterationCount() {
        return simplex_iteration_count;
    }
    
    public long getMipNodeCount() {
        return mip_node_count;
    }
}
