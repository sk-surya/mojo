package org.skor;

import java.util.*;
import org.skor.jhighs.wrapper.*;

/**
 * Example usage of the MILP framework
 */
public class ExampleUsage {
    
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--low-level")) {
            // Run low-level API version only
            largeScaleExampleLowLevel();
        } else if (args.length > 0 && args[0].equals("--compare")) {
			// warmup with simpleLPExample
			// simpleLPExample();
			largeScaleExample(5, 5, 0.5, 2, "warmup_solution_warmstart.json");


            // Run both versions for comparison
            System.out.println("Running high-level abstraction version:");
			
			long startTime = System.currentTimeMillis();
            largeScaleExample("solution_warmstart.json");
			long endTime = System.currentTimeMillis();

			System.out.println("High-level version time: " + (endTime - startTime) + "ms");
            
            System.out.println("\n" + "=".repeat(80) + "\n");
			System.out.println("Running low-level API version:");
            startTime = System.currentTimeMillis();
			largeScaleExampleLowLevel();
			endTime = System.currentTimeMillis();
			System.out.println("Low-level version time: " + (endTime - startTime) + "ms");
        } else {
            // Default: run all examples
            // Example 1: Simple LP problem
            simpleLPExample();
            
            // Example 2: Integer programming with incremental updates
            incrementalMIPExample();
            
            // Example 3: Large-scale model building
            largeScaleExample("solution_warmstart.json");
        }
    }
    
    /**
     * Simple LP: maximize 3x + 2y subject to constraints
     */
    public static void simpleLPExample() {
        System.out.println("\n=== Simple LP Example ===");
        
        Model model = new HighsModel();
        
        // Variables
        Variable x = model.addVariable("x", 0, 10, Model.VarType.CONTINUOUS);
        Variable y = model.addVariable("y", 0, 10, Model.VarType.CONTINUOUS);
        
        // Objective: maximize 3x + 2y
        model.setObjective(
            Expression.of(x, 3).plus(y, 2),
            Model.OptimizationSense.MAXIMIZE
        );
        
        // Constraints
        // x + y <= 8
        model.addConstraint(
            "capacity",
            Expression.of(x).plus(y),
            Model.ConstraintType.LESS_EQUAL,
            8
        );
        
        // 2x + y <= 14
        model.addConstraint(
            Expression.of(x, 2).plus(y),
            Model.ConstraintType.LESS_EQUAL,
            14
        );
        
        // Solve
        Solution solution = model.solve();
        
        System.out.println("Status: " + solution.getStatus());
        System.out.println("Objective: " + solution.getObjectiveValue());
        System.out.println("x = " + solution.getValue(x));
        System.out.println("y = " + solution.getValue(y));
        System.out.println("Solve time: " + solution.getSolveTimeMs() + "ms");
    }
    
    /**
     * MIP with incremental updates
     */
    public static void incrementalMIPExample() {
        System.out.println("\n=== Incremental MIP Example ===");
        
        Model model = new HighsModel();
        
        // Initial model: Knapsack problem
        int n = 5;
        double[] values = {10, 13, 18, 31, 7};
        double[] weights = {11, 15, 20, 35, 10};
        double capacity = 47;
        
        Variable[] items = new Variable[n];
        Expression objective = new Expression();
        Expression weightConstraint = new Expression();
        
        for (int i = 0; i < n; i++) {
            items[i] = model.addVariable("item_" + i, 0, 1, Model.VarType.BINARY);
            objective.plus(items[i], values[i]);
            weightConstraint.plus(items[i], weights[i]);
        }
        
        model.setObjective(objective, Model.OptimizationSense.MAXIMIZE);
        Constraint capacityCon = model.addConstraint(
            "capacity",
            weightConstraint,
            Model.ConstraintType.LESS_EQUAL,
            capacity
        );
        
        // First solve
        Solution sol1 = model.solve(SolverParams.quick());
        System.out.println("Initial solve:");
		System.out.println("Status: " + sol1.getStatus());
        System.out.println("  Objective: " + sol1.getObjectiveValue());
		System.out.println("Solve time: " + sol1.getSolveTimeMs() + "ms");
        System.out.println("  Selected items:");
        for (int i = 0; i < n; i++) {
            if (sol1.getValue(items[i]) > 0.5) {
                System.out.println("    Item " + i + " (value=" + values[i] + 
                                 ", weight=" + weights[i] + ")");
            }
        }
        
        // Incremental update: Change capacity
        System.out.println("\nAfter increasing capacity to 60:");
        model.updateConstraintRHS(capacityCon, 60);
        
        // Use warm start from previous solution
        Solution sol2 = model.solve(
            new SolverParams()
                .withWarmStart(false)
                .withOutputLevel(1)
        );
        
        System.out.println("  Objective: " + sol2.getObjectiveValue());
		System.out.println("  Solve time: " + sol2.getSolveTimeMs() + "ms");
        System.out.println("  Selected items:");
        for (int i = 0; i < n; i++) {
            if (sol2.getValue(items[i]) > 0.5) {
                System.out.println("    Item " + i);
            }
        }
        
        // Batch update: Add new item and modify objective
        System.out.println("\nAfter adding new item and changing (batch update) values:");
        model.beginUpdate();
        
        Variable newItem = model.addVariable("item_5", 0, 1, Model.VarType.BINARY);
        model.updateObjectiveCoefficient(newItem, 25);
        model.updateCoefficient(capacityCon, newItem, 22);
        
        // Change value of existing item
        model.updateObjectiveCoefficient(items[0], 15);
        
        model.endUpdate();
        
        Solution sol3 = model.solve();
        System.out.println("  Objective: " + sol3.getObjectiveValue());
        System.out.println("  Solve time: " + sol3.getSolveTimeMs() + "ms");
    }
    
    /**
     * Large-scale model building example
     */
    public static void largeScaleExample(String solutionFile) {
        largeScaleExample(10000, 5000, 0.5, 2, solutionFile);
    }
    
    /**
     * Large-scale model building example with configurable parameters
     * 
     * @param numVars Number of variables
     * @param numConstraints Number of constraints
     * @param density Constraint matrix density (0.0 to 1.0)
     * @param precision Number of decimal places for rounding (0 for integers)
     */
    public static void largeScaleExample(int numVars, int numConstraints, double density, int precision, String solutionFile) {
        System.out.println("\n=== Large-Scale Example ===");
        
        System.out.println("Building model with " + numVars + " variables and " + 
                          numConstraints + " constraints (precision: " + precision + " decimals)...");
        
        long startBuild = System.currentTimeMillis();
        
        Model model = new HighsModel();
        
        // Calculate rounding factor based on precision
        double roundingFactor = Math.pow(10, precision);
        
        // Use batch mode for efficient building
        model.beginUpdate();
        
        // Create variables
        Variable[] vars = new Variable[numVars];
        Expression objective = new Expression();
        
        for (int i = 0; i < numVars; i++) {
            // Mix of continuous and integer variables
            // Model.VarType type = (i % 10 == 0) ? Model.VarType.INTEGER : 
            //                     Model.VarType.CONTINUOUS;
			Model.VarType type = Model.VarType.CONTINUOUS;
            vars[i] = model.addVariable(0, 100, type);
            
            // Random objective coefficient
            double objCoeff = Math.round(Math.random() * 10 * roundingFactor) / roundingFactor;
            objective.plus(vars[i], objCoeff);
        }
        
        model.setObjective(objective, Model.OptimizationSense.MAXIMIZE);
        
        // Create constraints with sparse random coefficients
        java.util.Random rand = new java.util.Random(42);
        
        for (int i = 0; i < numConstraints; i++) {
            Expression expr = new Expression();
            
            // Add random coefficients
            int numNonZeros = (int)(numVars * density);
            for (int j = 0; j < numNonZeros; j++) {
                int varIdx = rand.nextInt(numVars);
                double coeff = Math.round(rand.nextDouble() * 10 * roundingFactor) / roundingFactor;
                expr.plus(vars[varIdx], coeff);
            }
            
            // Random RHS
            double rhs = Math.round(rand.nextDouble() * 1000 * roundingFactor) / roundingFactor;
            model.addConstraint(expr, Model.ConstraintType.LESS_EQUAL, rhs);
        }
        
        model.endUpdate();
        long buildTime = System.currentTimeMillis() - startBuild;
		System.out.println("Model built in " + buildTime + "ms");
		System.out.println("Writing model to large_model.lp...");
		// long startWrite = System.currentTimeMillis();
		// model.writeLP("large_model.lp");
		// long writeTime = System.currentTimeMillis() - startWrite;
		// System.out.println("Model written to large_model.lp in " + writeTime + "ms");
        
        Model.ModelStats stats = model.getStatistics();
        System.out.println("Model statistics:");
        System.out.println("  Variables: " + stats.numVariables);
        System.out.println("  Constraints: " + stats.numConstraints);
        System.out.println("  Non-zeros: " + stats.numNonZeros);
        System.out.println("  Integers: " + stats.numIntegers);
        
        // Solve with time limit
		int time_limit = 200;
        System.out.println("\nSolving with " + time_limit + "-second time limit...");
		// Load and apply as warm start
		try {
			var sol_values = Solution.loadFromFile(solutionFile, model);
			model.setWarmStartSolution(sol_values);
			System.out.println("Loaded " + sol_values.size() + " variable values for warm start");
		} catch (Exception e) {
			System.err.println("Error loading warm start solution: " + e.getMessage());
		}
        Solution solution = model.solve(
            new SolverParams()
                // .withTimeLimit(time_limit)
                .withGapTolerance(0.01)
                .withThreads(0)
				.withSolverSpecific("solver", "simplex")
				.withWarmStart(true)
        );

        
        System.out.println("Solution: " + solution);
        
        // Save solution to file
        try {
            solution.saveToFile(solutionFile);
            System.out.println("Solution saved to " + solutionFile);
        } catch (Exception e) {
            System.err.println("Error with solution persistence: " + e.getMessage());
        }

        // Demonstrate incremental change on large model
        System.out.println("\nPerforming incremental update on large model...");
        long startUpdate = System.currentTimeMillis();
        
        model.beginUpdate();
        // Change bounds on 100 variables
        for (int i = 0; i < numVars; i++) {
            model.updateVariableBounds(vars[i], 0, 0.5);
        }
        // Modify 50 constraint RHS values
        // (In real usage, you'd track constraint references)
        model.endUpdate();
        
        long updateTime = System.currentTimeMillis() - startUpdate;
        System.out.println("Update completed in " + updateTime + "ms");
        
        // Re-solve with warm start
        Solution solution2 = model.solve(
            new SolverParams()
                // .withTimeLimit(5)
                .withWarmStart(true)
        );
        
        System.out.println("Re-solved: " + solution2);
    }
    
    /**
     * Large-scale model building using low-level HiGHS API directly
     * This demonstrates the raw performance without the abstraction layer overhead
     * Uses addCol/addRow for incremental building (like the high-level API does)
     */
    public static void largeScaleExampleLowLevel(int numVars, int numConstraints, double density, int precision) {
        System.out.println("\n=== Large-Scale Example (Low-Level HiGHS API) ===");
        
        System.out.println("Building model with " + numVars + " variables and " + 
                          numConstraints + " constraints (precision: " + precision + " decimals)...");
        
        long startBuild = System.currentTimeMillis();
        
        // Calculate rounding factor based on precision
        double roundingFactor = Math.pow(10, precision);
        
        try (Highs highs = new Highs()) {
            // Set objective sense
            highs.changeObjectiveSense(ObjSense.kMaximize);
            
            // Add variables using addCol (like HighsModel does)
            double[] colCosts = new double[numVars];
            for (int i = 0; i < numVars; i++) {
                double objCoeff = Math.round(Math.random() * 10 * roundingFactor) / roundingFactor;
                colCosts[i] = objCoeff;
                
                // addCol(cost, lower, upper, numNonZeros, indices, values)
                highs.addCol(objCoeff, 0.0, 100.0, 0, new int[0], new double[0]);
            }
            
            // Build constraints - SAME logic as high-level version
            java.util.Random rand = new java.util.Random(42);
            
            int totalNonZeros = 0;
            for (int i = 0; i < numConstraints; i++) {
                Map<Integer, Double> expr = new HashMap<>();
                
                // Add random coefficients - SAME logic
                int numNonZeros = (int)(numVars * density);
                for (int j = 0; j < numNonZeros; j++) {
                    int varIdx = rand.nextInt(numVars);
                    double coeff = Math.round(rand.nextDouble() * 10 * roundingFactor) / roundingFactor;
                    // Accumulate like Expression.plus() does
                    expr.merge(varIdx, coeff, Double::sum);
                }
                
                // Convert to arrays for addRow
                int actualNonZeros = expr.size();
                int[] indices = new int[actualNonZeros];
                double[] values = new double[actualNonZeros];
                
                int idx = 0;
                for (Map.Entry<Integer, Double> entry : expr.entrySet()) {
                    indices[idx] = entry.getKey();
                    values[idx] = entry.getValue();
                    idx++;
                }
                
                // Random RHS - SAME logic
                double rhs = Math.round(rand.nextDouble() * 1000 * roundingFactor) / roundingFactor;
                
                // addRow(lower, upper, numNonZeros, indices, values)
                highs.addRow(Double.NEGATIVE_INFINITY, rhs, actualNonZeros, indices, values);
                
                totalNonZeros += actualNonZeros;
            }
            
            long buildTime = System.currentTimeMillis() - startBuild;
            System.out.println("Model built in " + buildTime + "ms");
            
            System.out.println("Model statistics:");
            System.out.println("  Variables: " + numVars);
            System.out.println("  Constraints: " + numConstraints);
            System.out.println("  Non-zeros: " + totalNonZeros);
            
            // Set solver options - SAME as high-level version
            System.out.println("\nSolving...");
            highs.setOptionValue("output_flag", true);
            highs.setOptionValue("log_to_console", true);
            highs.setOptionValue("solver", "simplex");
            highs.setOptionValue("threads", 0);
            highs.setOptionValue("mip_rel_gap", 0.01);
            
            // Solve
            long startSolve = System.currentTimeMillis();
            HighsStatus status = highs.run();
            long solveTime = System.currentTimeMillis() - startSolve;
            
            System.out.println("\nSolve status: " + status);
            System.out.println("Solve time: " + solveTime + "ms");
            
            // Get results
            HighsModelStatus modelStatus = highs.getModelStatus();
            System.out.println("Model status: " + modelStatus);
            
            if (modelStatus.isFeasible()) {
                HighsInfo info = highs.getInfo();
                System.out.println("Objective value: " + info.objective_function_value);
                System.out.println("Simplex iterations: " + info.simplex_iteration_count);
                
                HighsSolution solution = highs.getSolution();
                System.out.println("Solution vector size: " + solution.getColValue().length);
            }
            
            System.out.println("\nLow-level API test completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Overload for convenience
     */
    public static void largeScaleExampleLowLevel() {
        largeScaleExampleLowLevel(10000, 5000, 0.5, 2);
    }
}
