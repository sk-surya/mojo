package org.skor;

import java.util.List;
import java.util.Map;

/**
 * Core abstraction for MILP models, solver-agnostic
 */
public interface Model {
    
    // Variable management
    Variable addVariable(String name, double lb, double ub, VarType type);
    Variable addVariable(double lb, double ub, VarType type); // auto-named
    void updateVariableBounds(Variable var, double lb, double ub);
    void removeVariable(Variable var);
    
    // Constraint management
    Constraint addConstraint(String name, Expression expr, ConstraintType type, double rhs);
    Constraint addConstraint(Expression expr, ConstraintType type, double rhs); // auto-named
    Constraint addRangeConstraint(String name, Expression expr, double lb, double ub);
    void updateConstraintRHS(Constraint con, double rhs);
    void removeConstraint(Constraint con);
    
    // Objective
    void setObjective(Expression expr, OptimizationSense sense);
    void updateObjectiveCoefficient(Variable var, double coeff);
    
    // Model updates (for incremental changes)
    void updateCoefficient(Constraint con, Variable var, double coeff);
    void beginUpdate(); // Start batch update mode
    void endUpdate();   // Apply all pending updates
    
    // Solving
    Solution solve();
    Solution solve(SolverParams params);
    
    // Model inspection
    int getNumVariables();
    int getNumConstraints();
    int getNumNonZeros();
    ModelStats getStatistics();
    
    // Export/Import
    void writeLP(String filename);
    void writeMPS(String filename);
    
    enum VarType {
        CONTINUOUS, INTEGER, BINARY
    }
    
    enum ConstraintType {
        EQUAL, LESS_EQUAL, GREATER_EQUAL
    }
    
    enum OptimizationSense {
        MINIMIZE, MAXIMIZE
    }
    
    class ModelStats {
        public int numVariables;
        public int numConstraints;
        public int numNonZeros;
        public int numIntegers;
        public int numBinaries;
        public long buildTimeMs;
        public long solveTimeMs;
    }
}
