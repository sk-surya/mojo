package org.skor.jhighs.wrapper;

/**
 * Status of the model after solving.
 */
public enum HighsModelStatus {
    NOTSET(0),
    LOAD_ERROR(1),
    MODEL_ERROR(2),
    PRESOLVE_ERROR(3),
    SOLVE_ERROR(4),
    POSTSOLVE_ERROR(5),
    MODEL_EMPTY(6),
    OPTIMAL(7),
    INFEASIBLE(8),
    UNBOUNDED_OR_INFEASIBLE(9),
    UNBOUNDED(10),
    OBJECTIVE_BOUND(11),
    OBJECTIVE_TARGET(12),
    TIME_LIMIT(13),
    ITERATION_LIMIT(14),
    UNKNOWN(15),
    SOLUTION_LIMIT(16),
    INTERRUPT(17);
    
    private final int code;
    
    HighsModelStatus(int code) {
        this.code = code;
    }
    
    public int getCode() {
        return code;
    }
    
    public static HighsModelStatus fromCode(int code) {
        for (HighsModelStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return UNKNOWN;
    }
    
    public boolean isOptimal() {
        return this == OPTIMAL;
    }
    
    public boolean isFeasible() {
        return this == OPTIMAL || this == OBJECTIVE_BOUND || 
               this == OBJECTIVE_TARGET || this == TIME_LIMIT || 
               this == ITERATION_LIMIT || this == SOLUTION_LIMIT;
    }
}
