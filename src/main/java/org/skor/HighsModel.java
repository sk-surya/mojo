package org.skor;

import org.skor.jhighs.wrapper.*;
import java.util.*;

/**
 * HiGHS solver implementation with full incremental update support
 */
public class HighsModel extends BaseModel {
    
    private Highs highs;
    private boolean modelBuilt = false;
    
    // Mappings between our model and HiGHS indices
    private final Map<Variable, Integer> varToHighsIndex = new HashMap<>();
    private final Map<Constraint, Integer> conToHighsIndex = new HashMap<>();
    private final Map<Integer, Variable> highsIndexToVar = new HashMap<>();
    private final Map<Integer, Constraint> highsIndexToCon = new HashMap<>();
    
    // Deferred operations during batch updates
    private final List<Runnable> deferredOps = new ArrayList<>();
    
    // Warm start solution
    private Map<Variable, Double> warmStartSolution = null;
    
    public HighsModel() {
        this.highs = new Highs();
    }
    
    @Override
    public Solution solve() {
        return solve(new SolverParams());
    }
    
    @Override
    public Solution solve(SolverParams params) {
        if (!modelBuilt) {
            buildInitialModel();
        }
        
        // Apply solver parameters
        applyParams(params);
        
        // Apply warm start if available
        if (params.isWarmStart() && warmStartSolution != null) {
            applyWarmStart();
        }
        
        // Solve
		long startTime = System.currentTimeMillis();
        HighsStatus status = highs.run();
        
        lastSolveTimeMs = System.currentTimeMillis() - startTime;
        
        return extractSolution(status);
    }

    public HighsStatus setOptionValue(String key, Object value) {
        if (value instanceof Boolean) {
            return highs.setOptionValue(key, (Boolean) value);
        } else if (value instanceof String) {
            return highs.setOptionValue(key, (String) value);
        } else if (value instanceof Number) {
            return highs.setOptionValue(key, ((Number) value).doubleValue());
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
        }
    }

    private void buildInitialModel() {
        long startTime = System.currentTimeMillis();
        
        List<Variable> activeVars = getActiveVariables();
        List<Constraint> activeCons = getActiveConstraints();
        
        int numVars = activeVars.size();
        int numCons = activeCons.size();
        
        // Build variable arrays
        double[] colCost = new double[numVars];
        double[] colLower = new double[numVars];
        double[] colUpper = new double[numVars];
        HighsVarType[] integrality = new HighsVarType[numVars];
        
        for (int i = 0; i < numVars; i++) {
            Variable var = activeVars.get(i);
            varToHighsIndex.put(var, i);
            highsIndexToVar.put(i, var);
            
            colCost[i] = objectiveCoeffs.getOrDefault(var, 0.0);
            colLower[i] = var.getLowerBound();
            colUpper[i] = var.getUpperBound();
            
            switch (var.getType()) {
                case CONTINUOUS:
                    integrality[i] = HighsVarType.kContinuous;
                    break;
                case INTEGER:
                    integrality[i] = HighsVarType.kInteger;
                    break;
                case BINARY:
                    integrality[i] = HighsVarType.kInteger;
                    colLower[i] = Math.max(0, colLower[i]);
                    colUpper[i] = Math.min(1, colUpper[i]);
                    break;
            }
        }
        
        // Build constraint matrix in CSC format
        List<Integer> aStart = new ArrayList<>();
        List<Integer> aIndex = new ArrayList<>();
        List<Double> aValue = new ArrayList<>();
        double[] rowLower = new double[numCons];
        double[] rowUpper = new double[numCons];
        
        // First, build row-wise to get all coefficients
        Map<Integer, Map<Integer, Double>> colWiseMatrix = new HashMap<>();
        
        for (int rowIdx = 0; rowIdx < numCons; rowIdx++) {
            Constraint con = activeCons.get(rowIdx);
            conToHighsIndex.put(con, rowIdx);
            highsIndexToCon.put(rowIdx, con);
            
            // Set bounds based on constraint type
            if (con.isRange()) {
                rowLower[rowIdx] = con.getLhs();
                rowUpper[rowIdx] = con.getRhs();
            } else {
                switch (con.getType()) {
                    case EQUAL:
                        rowLower[rowIdx] = con.getRhs();
                        rowUpper[rowIdx] = con.getRhs();
                        break;
                    case LESS_EQUAL:
                        rowLower[rowIdx] = Double.NEGATIVE_INFINITY;
                        rowUpper[rowIdx] = con.getRhs();
                        break;
                    case GREATER_EQUAL:
                        rowLower[rowIdx] = con.getRhs();
                        rowUpper[rowIdx] = Double.POSITIVE_INFINITY;
                        break;
                }
            }
            
            // Add coefficients
            Map<Variable, Double> row = constraintMatrix.get(con);
            if (row != null) {
                for (Map.Entry<Variable, Double> entry : row.entrySet()) {
                    Integer colIdx = varToHighsIndex.get(entry.getKey());
                    if (colIdx != null) {
                        colWiseMatrix.computeIfAbsent(colIdx, k -> new HashMap<>())
                                    .put(rowIdx, entry.getValue());
                    }
                }
            }
        }
        
        // Convert to CSC format
        aStart.add(0);
        for (int colIdx = 0; colIdx < numVars; colIdx++) {
            Map<Integer, Double> column = colWiseMatrix.get(colIdx);
            if (column != null) {
                for (Map.Entry<Integer, Double> entry : column.entrySet()) {
                    aIndex.add(entry.getKey());
                    aValue.add(entry.getValue());
                }
            }
            aStart.add(aIndex.size());
        }
        
        // Set the model
        highs.passModel(
            numCons,
            numVars,
            aIndex.size(),
            1, // format = column-wise
            objectiveSense == OptimizationSense.MINIMIZE ? 
                ObjSense.kMinimize : ObjSense.kMaximize,
            objectiveConstant,
            colCost,
            colLower,
            colUpper,
            rowLower,
            rowUpper,
            aStart.stream().mapToInt(Integer::intValue).toArray(),
            aIndex.stream().mapToInt(Integer::intValue).toArray(),
            aValue.stream().mapToDouble(Double::doubleValue).toArray(),
            integrality
        );
        
        modelBuilt = true;
        lastBuildTimeMs = System.currentTimeMillis() - startTime;
    }
    
    private void applyParams(SolverParams params) {
        // Time limit
        if (params.getTimeLimit() < Double.POSITIVE_INFINITY) {
            highs.setOptionValue("time_limit", params.getTimeLimit());
        }
        
        // MIP gap
        highs.setOptionValue("mip_rel_gap", params.getGapTolerance());
        
        // Feasibility tolerance
        highs.setOptionValue("primal_feasibility_tolerance", params.getFeasibilityTolerance());
        highs.setOptionValue("dual_feasibility_tolerance", params.getOptimalityTolerance());
        
        // Threads
        if (params.getThreads() > 0) {
            HighsStatus status = highs.setOptionValue("threads", params.getThreads());
			if (status != HighsStatus.OK) {
				throw new RuntimeException("Failed to set threads option: " + status);
			}
        }
        
        // Presolve
        highs.setOptionValue("presolve", params.getPresolve().equals("auto") ? "choose" : params.getPresolve());
        
        // Output level
        highs.setOptionValue("output_flag", params.getOutputLevel() > 0);
        if (params.getOutputLevel() > 1) {
            highs.setOptionValue("log_to_console", true);
        }
        
        // Solver-specific parameters
        for (Map.Entry<String, Object> entry : params.getSolverSpecific().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Number) {
                highs.setOptionValue(key, ((Number) value).doubleValue());
            } else if (value instanceof String) {
                highs.setOptionValue(key, (String) value);
            } else if (value instanceof Boolean) {
                highs.setOptionValue(key, (Boolean) value);
            }
        }
    }
    
    private void applyWarmStart() {
        if (warmStartSolution == null || warmStartSolution.isEmpty()) return;
        
        double[] solution = new double[varToHighsIndex.size()];
        for (Map.Entry<Variable, Integer> entry : varToHighsIndex.entrySet()) {
            Double value = warmStartSolution.get(entry.getKey());
            solution[entry.getValue()] = value != null ? value : 0.0;
        }
        
        highs.setSolution(solution);
    }
    
    private Solution extractSolution(HighsStatus status) {
        Solution.Builder builder = new Solution.Builder();
        
        // Map HiGHS status to our status
        builder.status(mapStatus(status));
        
        // Get solution info
        HighsInfo info = highs.getInfo();
        builder.objectiveValue(info.objective_function_value)
               .gap(info.mip_gap)
               .iterations((int) info.simplex_iteration_count)
               .nodeCount((int) info.mip_node_count)
               .solveTimeMs(lastSolveTimeMs);
        
        // Extract variable values
        if (builder.build().isFeasible()) {
            HighsSolution solution = highs.getSolution();
            for (Map.Entry<Variable, Integer> entry : varToHighsIndex.entrySet()) {
                double value = solution.getColValue()[entry.getValue()];
                builder.variableValue(entry.getKey(), value);
            }
            
            // Extract dual values for LP problems
            if (variables.stream().allMatch(v -> v.getType() == Model.VarType.CONTINUOUS)) {
                for (Map.Entry<Constraint, Integer> entry : conToHighsIndex.entrySet()) {
                    double dual = solution.getRowDual()[entry.getValue()];
                    builder.dualValue(entry.getKey(), dual);
                }
            }
        }
        
        // Store for potential warm start
        if (builder.build().isFeasible()) {
            warmStartSolution = builder.build().getVariableValues();
        }
        
        return builder.build();
    }
    
    private Solution.Status mapStatus(HighsStatus status) {
        if (status == HighsStatus.OK) {
            HighsModelStatus modelStatus = highs.getModelStatus();
            switch (modelStatus) {
                case OPTIMAL:
                    return Solution.Status.OPTIMAL;
                case INFEASIBLE:
                    return Solution.Status.INFEASIBLE;
                case UNBOUNDED:
                case UNBOUNDED_OR_INFEASIBLE:
                    return Solution.Status.UNBOUNDED;
                case TIME_LIMIT:
                    return Solution.Status.TIME_LIMIT;
                case ITERATION_LIMIT:
                    return Solution.Status.ITERATION_LIMIT;
                case SOLUTION_LIMIT:
                    return Solution.Status.SOLUTION_LIMIT;
                default:
                    return Solution.Status.UNKNOWN;
            }
        } else if (status == HighsStatus.ERROR) {
            return Solution.Status.NUMERICAL_ERROR;
        } else {
            return Solution.Status.UNKNOWN;
        }
    }
    
    // Incremental update methods
    @Override
    protected void onVariableAdded(Variable var) {
        if (!modelBuilt) return;
        
        Runnable op = () -> {
            int colIdx = varToHighsIndex.size();
            varToHighsIndex.put(var, colIdx);
            highsIndexToVar.put(colIdx, var);
            
            double cost = objectiveCoeffs.getOrDefault(var, 0.0);
            HighsVarType integrality = var.getType() == Model.VarType.CONTINUOUS ?
                HighsVarType.kContinuous : HighsVarType.kInteger;
            
            highs.addCol(cost, var.getLowerBound(), var.getUpperBound(),
                        0, new int[0], new double[0]);
            
            if (integrality != HighsVarType.kContinuous) {
                highs.changeColIntegrality(colIdx, integrality);
            }
        };
        
        if (inBatchUpdate) {
            deferredOps.add(op);
        } else {
            op.run();
        }
    }
    
    @Override
    protected void onVariableBoundsChanged(Variable var) {
        if (!modelBuilt) return;
        
        Integer colIdx = varToHighsIndex.get(var);
        if (colIdx == null) return;
        
        Runnable op = () -> {
            highs.changeColBounds(colIdx, var.getLowerBound(), var.getUpperBound());
        };
        
        if (inBatchUpdate) {
            deferredOps.add(op);
        } else {
            op.run();
        }
    }
    
    @Override
    protected void onVariableRemoved(Variable var) {
        if (!modelBuilt) return;
        
        Integer colIdx = varToHighsIndex.get(var);
        if (colIdx == null) return;
        
        Runnable op = () -> {
            boolean[] mask = new boolean[varToHighsIndex.size()];
            mask[colIdx] = true;
            highs.deleteColsByMask(mask);
            
            // Rebuild index mappings
            rebuildVariableIndices();
        };
        
        if (inBatchUpdate) {
            deferredOps.add(op);
        } else {
            op.run();
        }
    }
    
    @Override
    protected void onConstraintAdded(Constraint con) {
        if (!modelBuilt) return;
        
        Runnable op = () -> {
            int rowIdx = conToHighsIndex.size();
            conToHighsIndex.put(con, rowIdx);
            highsIndexToCon.put(rowIdx, con);
            
            // Collect non-zero coefficients
            Map<Variable, Double> row = constraintMatrix.get(con);
            List<Integer> indices = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            
            if (row != null) {
                for (Map.Entry<Variable, Double> entry : row.entrySet()) {
                    Integer colIdx = varToHighsIndex.get(entry.getKey());
                    if (colIdx != null) {
                        indices.add(colIdx);
                        values.add(entry.getValue());
                    }
                }
            }
            
            double lower, upper;
            if (con.isRange()) {
                lower = con.getLhs();
                upper = con.getRhs();
            } else {
                switch (con.getType()) {
                    case EQUAL:
                        lower = upper = con.getRhs();
                        break;
                    case LESS_EQUAL:
                        lower = Double.NEGATIVE_INFINITY;
                        upper = con.getRhs();
                        break;
                    case GREATER_EQUAL:
                        lower = con.getRhs();
                        upper = Double.POSITIVE_INFINITY;
                        break;
                    default:
                        lower = upper = 0;
                }
            }
            
            highs.addRow(lower, upper, indices.size(),
                        indices.stream().mapToInt(Integer::intValue).toArray(),
                        values.stream().mapToDouble(Double::doubleValue).toArray());
        };
        
        if (inBatchUpdate) {
            deferredOps.add(op);
        } else {
            op.run();
        }
    }
    
    @Override
    protected void onConstraintRHSChanged(Constraint con) {
        if (!modelBuilt) return;
        
        Integer rowIdx = conToHighsIndex.get(con);
        if (rowIdx == null) return;
        
        Runnable op = () -> {
            if (con.isRange()) {
                highs.changeRowBounds(rowIdx, con.getLhs(), con.getRhs());
            } else {
                double lower, upper;
                switch (con.getType()) {
                    case EQUAL:
                        lower = upper = con.getRhs();
                        break;
                    case LESS_EQUAL:
                        lower = Double.NEGATIVE_INFINITY;
                        upper = con.getRhs();
                        break;
                    case GREATER_EQUAL:
                        lower = con.getRhs();
                        upper = Double.POSITIVE_INFINITY;
                        break;
                    default:
                        return;
                }
                highs.changeRowBounds(rowIdx, lower, upper);
            }
        };
        
        if (inBatchUpdate) {
            deferredOps.add(op);
        } else {
            op.run();
        }
    }
    
    @Override
    protected void onConstraintModified(Constraint con) {
        // For major constraint modifications, it's often better to delete and re-add
        if (!modelBuilt) return;
        
        Integer rowIdx = conToHighsIndex.get(con);
        if (rowIdx == null) return;
        
        // This is a simplified approach - for production, you might want to
        // track specific coefficient changes and use changeCoeff
        onConstraintRemoved(con);
        onConstraintAdded(con);
    }
    
    @Override
    protected void onConstraintRemoved(Constraint con) {
        if (!modelBuilt) return;
        
        Integer rowIdx = conToHighsIndex.get(con);
        if (rowIdx == null) return;
        
        Runnable op = () -> {
            boolean[] mask = new boolean[conToHighsIndex.size()];
            mask[rowIdx] = true;
            highs.deleteRowsByMask(mask);
            
            // Rebuild index mappings
            rebuildConstraintIndices();
        };
        
        if (inBatchUpdate) {
            deferredOps.add(op);
        } else {
            op.run();
        }
    }
    
    @Override
    protected void onObjectiveChanged() {
        if (!modelBuilt) return;
        
        Runnable op = () -> {
            // Update sense
            highs.changeObjectiveSense(objectiveSense == OptimizationSense.MINIMIZE ?
                ObjSense.kMinimize : ObjSense.kMaximize);
            
            // Update offset
            highs.changeObjectiveOffset(objectiveConstant);
            
            // Update all coefficients
            for (Map.Entry<Variable, Integer> entry : varToHighsIndex.entrySet()) {
                double coeff = objectiveCoeffs.getOrDefault(entry.getKey(), 0.0);
                highs.changeColCost(entry.getValue(), coeff);
            }
        };
        
        if (inBatchUpdate) {
            deferredOps.add(op);
        } else {
            op.run();
        }
    }
    
    @Override
    protected void onObjectiveCoefficientChanged(Variable var, double coeff) {
        if (!modelBuilt) return;
        
        Integer colIdx = varToHighsIndex.get(var);
        if (colIdx == null) return;
        
        Runnable op = () -> {
            highs.changeColCost(colIdx, coeff);
        };
        
        if (inBatchUpdate) {
            deferredOps.add(op);
        } else {
            op.run();
        }
    }
    
    @Override
    protected void onCoefficientChanged(Constraint con, Variable var, double coeff) {
        if (!modelBuilt) return;
        
        Integer rowIdx = conToHighsIndex.get(con);
        Integer colIdx = varToHighsIndex.get(var);
        if (rowIdx == null || colIdx == null) return;
        
        Runnable op = () -> {
            highs.changeCoeff(rowIdx, colIdx, coeff);
        };
        
        if (inBatchUpdate) {
            deferredOps.add(op);
        } else {
            op.run();
        }
    }
    
    @Override
    protected void onBeginUpdate() {
        deferredOps.clear();
    }
    
    @Override
    protected void onEndUpdate() {
        // Execute all deferred operations
        for (Runnable op : deferredOps) {
            op.run();
        }
        deferredOps.clear();
    }
    
    @Override
    public void writeLP(String filename) {
        if (!modelBuilt) {
            buildInitialModel();
        }
        highs.writeModel(filename);
    }
    
    @Override
    public void writeMPS(String filename) {
        if (!modelBuilt) {
            buildInitialModel();
        }
        highs.writeModel(filename);
    }
    
    private void rebuildVariableIndices() {
        varToHighsIndex.clear();
        highsIndexToVar.clear();
        
        int idx = 0;
        for (Variable var : getActiveVariables()) {
            varToHighsIndex.put(var, idx);
            highsIndexToVar.put(idx, var);
            idx++;
        }
    }
    
    private void rebuildConstraintIndices() {
        conToHighsIndex.clear();
        highsIndexToCon.clear();
        
        int idx = 0;
        for (Constraint con : getActiveConstraints()) {
            conToHighsIndex.put(con, idx);
            highsIndexToCon.put(idx, con);
            idx++;
        }
    }
    
    public void dispose() {
        if (highs != null) {
            highs.dispose();
            highs = null;
        }
    }
}
