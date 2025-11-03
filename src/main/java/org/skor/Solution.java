package org.skor;

import java.util.*;

/**
 * Represents the solution from the solver
 */
public class Solution {
    
    public enum Status {
        OPTIMAL,
        FEASIBLE,
        INFEASIBLE,
        UNBOUNDED,
        TIME_LIMIT,
        ITERATION_LIMIT,
        NODE_LIMIT,
        SOLUTION_LIMIT,
        INTERRUPTED,
        NUMERICAL_ERROR,
        UNKNOWN
    }
    
    private final Status status;
    private final double objectiveValue;
    private final Map<Variable, Double> variableValues;
    private final Map<Constraint, Double> dualValues;
    private final double gap; // MIP gap
    private final long solveTimeMs;
    private final int iterations;
    private final int nodeCount;
    
    private Solution(Builder builder) {
        this.status = builder.status;
        this.objectiveValue = builder.objectiveValue;
        this.variableValues = Collections.unmodifiableMap(new HashMap<>(builder.variableValues));
        this.dualValues = Collections.unmodifiableMap(new HashMap<>(builder.dualValues));
        this.gap = builder.gap;
        this.solveTimeMs = builder.solveTimeMs;
        this.iterations = builder.iterations;
        this.nodeCount = builder.nodeCount;
    }
    
    public Status getStatus() { return status; }
    public double getObjectiveValue() { return objectiveValue; }
    public double getGap() { return gap; }
    public long getSolveTimeMs() { return solveTimeMs; }
    public int getIterations() { return iterations; }
    public int getNodeCount() { return nodeCount; }
    
    public double getValue(Variable var) {
        return variableValues.getOrDefault(var, 0.0);
    }
    
    public double getDual(Constraint con) {
        return dualValues.getOrDefault(con, 0.0);
    }
    
    public Map<Variable, Double> getVariableValues() {
        return variableValues;
    }
    
    public Map<Constraint, Double> getDualValues() {
        return dualValues;
    }
    
    public boolean isOptimal() {
        return status == Status.OPTIMAL;
    }
    
    public boolean isFeasible() {
        return status == Status.OPTIMAL || status == Status.FEASIBLE;
    }
    
    // Builder pattern for solution construction
    public static class Builder {
        private Status status = Status.UNKNOWN;
        private double objectiveValue = Double.NaN;
        private Map<Variable, Double> variableValues = new HashMap<>();
        private Map<Constraint, Double> dualValues = new HashMap<>();
        private double gap = 0.0;
        private long solveTimeMs = 0;
        private int iterations = 0;
        private int nodeCount = 0;
        
        public Builder status(Status status) {
            this.status = status;
            return this;
        }
        
        public Builder objectiveValue(double value) {
            this.objectiveValue = value;
            return this;
        }
        
        public Builder variableValue(Variable var, double value) {
            this.variableValues.put(var, value);
            return this;
        }
        
        public Builder variableValues(Map<Variable, Double> values) {
            this.variableValues.putAll(values);
            return this;
        }
        
        public Builder dualValue(Constraint con, double value) {
            this.dualValues.put(con, value);
            return this;
        }
        
        public Builder dualValues(Map<Constraint, Double> values) {
            this.dualValues.putAll(values);
            return this;
        }
        
        public Builder gap(double gap) {
            this.gap = gap;
            return this;
        }
        
        public Builder solveTimeMs(long time) {
            this.solveTimeMs = time;
            return this;
        }
        
        public Builder iterations(int iterations) {
            this.iterations = iterations;
            return this;
        }
        
        public Builder nodeCount(int nodes) {
            this.nodeCount = nodes;
            return this;
        }
        
        public Solution build() {
            return new Solution(this);
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Solution[status=").append(status);
        if (isFeasible()) {
            sb.append(", objective=").append(objectiveValue);
            if (gap > 0) {
                sb.append(", gap=").append(String.format("%.2f%%", gap * 100));
            }
        }
        sb.append(", time=").append(solveTimeMs).append("ms");
        if (nodeCount > 0) {
            sb.append(", nodes=").append(nodeCount);
        }
        sb.append("]");
        return sb.toString();
    }
}
