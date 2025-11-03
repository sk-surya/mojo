package org.skor;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base implementation providing common functionality for all models
 */
public abstract class BaseModel implements Model {
    
    // Model structure
    protected final List<Variable> variables = new ArrayList<>();
    protected final Map<String, Variable> variablesByName = new HashMap<>();
    protected final Map<Variable, Double> objectiveCoeffs = new HashMap<>();
    protected final List<Constraint> constraints = new ArrayList<>();
    protected final Map<Constraint, Map<Variable, Double>> constraintMatrix = new HashMap<>();
    
    // Metadata
    protected OptimizationSense objectiveSense = OptimizationSense.MINIMIZE;
    protected double objectiveConstant = 0.0;
    
    // Tracking
    protected final Set<Variable> deletedVariables = new HashSet<>();
    protected final Set<Constraint> deletedConstraints = new HashSet<>();
    protected boolean inBatchUpdate = false;
    protected final Set<Variable> modifiedVariables = new HashSet<>();
    protected final Set<Constraint> modifiedConstraints = new HashSet<>();
    protected boolean objectiveModified = false;
    
    // Name generation
    private final AtomicInteger varCounter = new AtomicInteger(0);
    private final AtomicInteger conCounter = new AtomicInteger(0);
    
    // Statistics
    protected long lastBuildTimeMs = 0;
    protected long lastSolveTimeMs = 0;
    
    @Override
    public Variable addVariable(String name, double lb, double ub, VarType type) {
        Variable var = new Variable(variables.size(), name, lb, ub, type);
        variables.add(var);
        variablesByName.put(name, var);
        
        if (!inBatchUpdate) {
            onVariableAdded(var);
        }
        
        return var;
    }
    
    @Override
    public Variable addVariable(double lb, double ub, VarType type) {
        String name = "x" + varCounter.getAndIncrement();
        return addVariable(name, lb, ub, type);
    }
    
    @Override
    public void updateVariableBounds(Variable var, double lb, double ub) {
        if (deletedVariables.contains(var)) {
            throw new IllegalArgumentException("Variable has been deleted: " + var.getName());
        }
        
        var.setLowerBound(lb);
        var.setUpperBound(ub);
        modifiedVariables.add(var);
        
        if (!inBatchUpdate) {
            onVariableBoundsChanged(var);
        }
    }
    
    @Override
    public void removeVariable(Variable var) {
        if (deletedVariables.contains(var)) return;
        
        deletedVariables.add(var);
        variablesByName.remove(var.getName());
        
        // Remove from objective
        objectiveCoeffs.remove(var);
        
        // Remove from all constraints
        for (Map<Variable, Double> row : constraintMatrix.values()) {
            row.remove(var);
        }
        
        if (!inBatchUpdate) {
            onVariableRemoved(var);
        }
    }
    
    @Override
    public Constraint addConstraint(String name, Expression expr, ConstraintType type, double rhs) {
        Constraint con = new Constraint(constraints.size(), name, type, rhs);
        constraints.add(con);
        
        // Store constraint coefficients
        Map<Variable, Double> row = new HashMap<>();
        expr.forEachTerm(row::put);
        constraintMatrix.put(con, row);
        
        if (!inBatchUpdate) {
            onConstraintAdded(con);
        }
        
        return con;
    }
    
    @Override
    public Constraint addConstraint(Expression expr, ConstraintType type, double rhs) {
        String name = "c" + conCounter.getAndIncrement();
        return addConstraint(name, expr, type, rhs);
    }
    
    @Override
    public Constraint addRangeConstraint(String name, Expression expr, double lb, double ub) {
        Constraint con = new Constraint(constraints.size(), name, lb, ub);
        constraints.add(con);
        
        // Store constraint coefficients
        Map<Variable, Double> row = new HashMap<>();
        expr.forEachTerm(row::put);
        constraintMatrix.put(con, row);
        
        if (!inBatchUpdate) {
            onConstraintAdded(con);
        }
        
        return con;
    }
    
    @Override
    public void updateConstraintRHS(Constraint con, double rhs) {
        if (deletedConstraints.contains(con)) {
            throw new IllegalArgumentException("Constraint has been deleted: " + con.getName());
        }
        
        con.setRhs(rhs);
        modifiedConstraints.add(con);
        
        if (!inBatchUpdate) {
            onConstraintRHSChanged(con);
        }
    }
    
    @Override
    public void removeConstraint(Constraint con) {
        if (deletedConstraints.contains(con)) return;
        
        deletedConstraints.add(con);
        constraintMatrix.remove(con);
        
        if (!inBatchUpdate) {
            onConstraintRemoved(con);
        }
    }
    
    @Override
    public void setObjective(Expression expr, OptimizationSense sense) {
        this.objectiveSense = sense;
        this.objectiveConstant = expr.getConstant();
        
        objectiveCoeffs.clear();
        expr.forEachTerm(objectiveCoeffs::put);
        objectiveModified = true;
        
        if (!inBatchUpdate) {
            onObjectiveChanged();
        }
    }
    
    @Override
    public void updateObjectiveCoefficient(Variable var, double coeff) {
        if (deletedVariables.contains(var)) {
            throw new IllegalArgumentException("Variable has been deleted: " + var.getName());
        }
        
        if (Math.abs(coeff) < 1e-10) {
            objectiveCoeffs.remove(var);
        } else {
            objectiveCoeffs.put(var, coeff);
        }
        objectiveModified = true;
        
        if (!inBatchUpdate) {
            onObjectiveCoefficientChanged(var, coeff);
        }
    }
    
    @Override
    public void updateCoefficient(Constraint con, Variable var, double coeff) {
        if (deletedConstraints.contains(con)) {
            throw new IllegalArgumentException("Constraint has been deleted: " + con.getName());
        }
        if (deletedVariables.contains(var)) {
            throw new IllegalArgumentException("Variable has been deleted: " + var.getName());
        }
        
        Map<Variable, Double> row = constraintMatrix.get(con);
        if (row == null) {
            row = new HashMap<>();
            constraintMatrix.put(con, row);
        }
        
        if (Math.abs(coeff) < 1e-10) {
            row.remove(var);
        } else {
            row.put(var, coeff);
        }
        modifiedConstraints.add(con);
        
        if (!inBatchUpdate) {
            onCoefficientChanged(con, var, coeff);
        }
    }
    
    @Override
    public void beginUpdate() {
        inBatchUpdate = true;
        modifiedVariables.clear();
        modifiedConstraints.clear();
        objectiveModified = false;
        onBeginUpdate();
    }
    
    @Override
    public void endUpdate() {
        if (!inBatchUpdate) return;
        
        inBatchUpdate = false;
        
        // Apply all pending updates
        for (Variable var : modifiedVariables) {
            onVariableBoundsChanged(var);
        }
        
        for (Constraint con : modifiedConstraints) {
            onConstraintModified(con);
        }
        
        if (objectiveModified) {
            onObjectiveChanged();
        }
        
        onEndUpdate();
        
        modifiedVariables.clear();
        modifiedConstraints.clear();
        objectiveModified = false;
    }
    
    @Override
    public int getNumVariables() {
        return variables.size() - deletedVariables.size();
    }
    
    @Override
    public int getNumConstraints() {
        return constraints.size() - deletedConstraints.size();
    }
    
    @Override
    public int getNumNonZeros() {
        int nnz = 0;
        for (Map<Variable, Double> row : constraintMatrix.values()) {
            nnz += row.size();
        }
        return nnz;
    }
    
    @Override
    public ModelStats getStatistics() {
        ModelStats stats = new ModelStats();
        stats.numVariables = getNumVariables();
        stats.numConstraints = getNumConstraints();
        stats.numNonZeros = getNumNonZeros();
        
        int numInt = 0, numBin = 0;
        for (Variable var : variables) {
            if (deletedVariables.contains(var)) continue;
            if (var.getType() == VarType.BINARY) numBin++;
            else if (var.getType() == VarType.INTEGER) numInt++;
        }
        stats.numIntegers = numInt;
        stats.numBinaries = numBin;
        stats.buildTimeMs = lastBuildTimeMs;
        stats.solveTimeMs = lastSolveTimeMs;
        
        return stats;
    }
    
    // Hook methods for subclasses to implement incremental updates
    protected abstract void onVariableAdded(Variable var);
    protected abstract void onVariableBoundsChanged(Variable var);
    protected abstract void onVariableRemoved(Variable var);
    
    protected abstract void onConstraintAdded(Constraint con);
    protected abstract void onConstraintRHSChanged(Constraint con);
    protected abstract void onConstraintModified(Constraint con);
    protected abstract void onConstraintRemoved(Constraint con);
    
    protected abstract void onObjectiveChanged();
    protected abstract void onObjectiveCoefficientChanged(Variable var, double coeff);
    
    protected abstract void onCoefficientChanged(Constraint con, Variable var, double coeff);
    
    protected abstract void onBeginUpdate();
    protected abstract void onEndUpdate();
    
    // Helper method to get active (non-deleted) variables
    protected List<Variable> getActiveVariables() {
        List<Variable> active = new ArrayList<>();
        for (Variable var : variables) {
            if (!deletedVariables.contains(var)) {
                active.add(var);
            }
        }
        return active;
    }
    
    // Helper method to get active (non-deleted) constraints
    protected List<Constraint> getActiveConstraints() {
        List<Constraint> active = new ArrayList<>();
        for (Constraint con : constraints) {
            if (!deletedConstraints.contains(con)) {
                active.add(con);
            }
        }
        return active;
    }
    
    @Override
    public Variable getVariableByName(String name) {
        Variable var = variablesByName.get(name);
        // Return null if variable was deleted
        return (var != null && !deletedVariables.contains(var)) ? var : null;
    }
}
