package org.skor;

import org.skor.jhighs.wrapper.*;

public class Main {
    
    public static void test_highs_wrapper(String[] args) {
        System.out.println("Testing HiGHS wrapper...");
        
        // Create a HiGHS instance using try-with-resources for automatic cleanup
        try (Highs highs = new Highs()) {
            System.out.println("HiGHS instance created successfully");
            
            // Simple test: minimize x + y subject to x + y >= 1, 0 <= x,y <= 10
            int numCol = 2;
            int numRow = 1;
            int numNz = 2;
            
            double[] colCost = {1.0, 1.0};
            double[] colLower = {0.0, 0.0};
            double[] colUpper = {10.0, 10.0};
            double[] rowLower = {1.0};
            double[] rowUpper = {Double.POSITIVE_INFINITY};
            int[] aStart = {0, 1, 2};
            int[] aIndex = {0, 0};
            double[] aValue = {1.0, 1.0};
            
            HighsStatus status = highs.passModel(
                numRow, numCol, numNz, 1, // 1 = column-wise format
                ObjSense.kMinimize, 0.0,
                colCost, colLower, colUpper,
                rowLower, rowUpper,
                aStart, aIndex, aValue,
                null // null = LP problem
            );
            
            System.out.println("Model passed: " + status);
            
            // Solve
            status = highs.run();
            System.out.println("Solve status: " + status);
            
            // Get results
            HighsModelStatus modelStatus = highs.getModelStatus();
            System.out.println("Model status: " + modelStatus);
            
            if (modelStatus.isFeasible()) {
                HighsSolution solution = highs.getSolution();
                HighsInfo info = highs.getInfo();
                
                System.out.println("Objective value: " + info.getObjectiveValue());
                System.out.println("Solution: x = " + solution.getColValue()[0] + 
                                   ", y = " + solution.getColValue()[1]);
            }
            
            System.out.println("\nHiGHS wrapper test completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

	public static void test_highs_model_wrapper(String[] args){
		System.out.println("\nTesting HighsModel wrapper...");
		
		try {
			// Create model
			HighsModel model = new HighsModel();
			
			// minimize x + y subject to x + y >= 1, 0 <= x,y <= 10
			
			// Add variables
			Variable x = model.addVariable("x", 0.0, 10.0, Model.VarType.CONTINUOUS);
			Variable y = model.addVariable("y", 0.0, 10.0, Model.VarType.CONTINUOUS);
			
			// Set objective: minimize x + y
			Expression objective = new Expression()
				.plus(x, 1.0)
				.plus(y, 1.0);
			model.setObjective(objective, Model.OptimizationSense.MINIMIZE);
			
			// Add constraint: x + y >= 1
			Expression constraint = new Expression()
				.plus(x, 1.0)
				.plus(y, 1.0);
			model.addConstraint("c1", constraint, Model.ConstraintType.GREATER_EQUAL, 1.0);
			
			// Solve
			Solution solution = model.solve();
			
			System.out.println("Solution status: " + solution.getStatus());
			System.out.println("Objective value: " + solution.getObjectiveValue());
			System.out.println("x = " + solution.getValue(x));
			System.out.println("y = " + solution.getValue(y));
			
			System.out.println("\nHighsModel wrapper test completed successfully!");
			
			// Clean up
			model.dispose();
			
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void benchmark(String[] args) {
		System.out.println("=".repeat(80));
		System.out.println("BENCHMARK: Comparing low-level vs high-level HiGHS API");
		System.out.println("=".repeat(80));
		
		int warmupRuns = 5;
		int benchmarkRuns = 100;
		
		// Warmup phase
		System.out.println("\nWarming up...");
		for (int i = 0; i < warmupRuns; i++) {
			test_highs_wrapper_silent();
			test_highs_model_wrapper_silent();
		}
		
		// Benchmark low-level wrapper
		System.out.println("\nBenchmarking low-level wrapper (" + benchmarkRuns + " runs)...");
		long[] lowLevelTimes = new long[benchmarkRuns];
		for (int i = 0; i < benchmarkRuns; i++) {
			long start = System.nanoTime();
			test_highs_wrapper_silent();
			long end = System.nanoTime();
			lowLevelTimes[i] = end - start;
		}
		
		// Benchmark high-level model wrapper
		System.out.println("Benchmarking high-level wrapper (" + benchmarkRuns + " runs)...");
		long[] highLevelTimes = new long[benchmarkRuns];
		for (int i = 0; i < benchmarkRuns; i++) {
			long start = System.nanoTime();
			test_highs_model_wrapper_silent();
			long end = System.nanoTime();
			highLevelTimes[i] = end - start;
		}

		// // Benchmark low-level wrapper
		// System.out.println("\nBenchmarking low-level wrapper (" + benchmarkRuns + " runs)...");
		// long[] lowLevelTimes = new long[benchmarkRuns];
		// for (int i = 0; i < benchmarkRuns; i++) {
		// 	long start = System.nanoTime();
		// 	test_highs_wrapper_silent();
		// 	long end = System.nanoTime();
		// 	lowLevelTimes[i] = end - start;
		// }
		
		// Calculate statistics
		double lowLevelAvg = average(lowLevelTimes);
		double lowLevelStd = stddev(lowLevelTimes, lowLevelAvg);
		double lowLevelMin = min(lowLevelTimes);
		double lowLevelMax = max(lowLevelTimes);
		
		double highLevelAvg = average(highLevelTimes);
		double highLevelStd = stddev(highLevelTimes, highLevelAvg);
		double highLevelMin = min(highLevelTimes);
		double highLevelMax = max(highLevelTimes);
		
		// Print results
		System.out.println("\n" + "=".repeat(80));
		System.out.println("RESULTS");
		System.out.println("=".repeat(80));
		
		System.out.printf("\nLow-level wrapper:\n");
		System.out.printf("  Average: %.3f ms\n", lowLevelAvg / 1_000_000.0);
		System.out.printf("  Std Dev: %.3f ms\n", lowLevelStd / 1_000_000.0);
		System.out.printf("  Min:     %.3f ms\n", lowLevelMin / 1_000_000.0);
		System.out.printf("  Max:     %.3f ms\n", lowLevelMax / 1_000_000.0);
		
		System.out.printf("\nHigh-level wrapper:\n");
		System.out.printf("  Average: %.3f ms\n", highLevelAvg / 1_000_000.0);
		System.out.printf("  Std Dev: %.3f ms\n", highLevelStd / 1_000_000.0);
		System.out.printf("  Min:     %.3f ms\n", highLevelMin / 1_000_000.0);
		System.out.printf("  Max:     %.3f ms\n", highLevelMax / 1_000_000.0);
		
		System.out.printf("\nOverhead:\n");
		System.out.printf("  Absolute: %.3f ms (%.2f%%)\n", 
			(highLevelAvg - lowLevelAvg) / 1_000_000.0,
			((highLevelAvg / lowLevelAvg) - 1.0) * 100.0);
		
		System.out.println("\n" + "=".repeat(80));
	}
	
	private static void test_highs_wrapper_silent() {
		try (Highs highs = new Highs()) {
			int numCol = 2, numRow = 1, numNz = 2;
			double[] colCost = {1.0, 1.0};
			double[] colLower = {0.0, 0.0};
			double[] colUpper = {10.0, 10.0};
			double[] rowLower = {1.0};
			double[] rowUpper = {Double.POSITIVE_INFINITY};
			int[] aStart = {0, 1, 2};
			int[] aIndex = {0, 0};
			double[] aValue = {1.0, 1.0};
			highs.setOptionValue("output_flag", false);
			highs.setOptionValue("log_to_console", false);
			highs.passModel(numRow, numCol, numNz, 1, ObjSense.kMinimize, 0.0,
				colCost, colLower, colUpper, rowLower, rowUpper,
				aStart, aIndex, aValue, null);
			highs.run();
		} catch (Exception e) {
			// Silent
		}
	}
	
	private static void test_highs_model_wrapper_silent() {
		try {
			HighsModel model = new HighsModel();
			Variable x = model.addVariable("x", 0.0, 10.0, Model.VarType.CONTINUOUS);
			Variable y = model.addVariable("y", 0.0, 10.0, Model.VarType.CONTINUOUS);
			Expression objective = new Expression().plus(x, 1.0).plus(y, 1.0);
			model.setObjective(objective, Model.OptimizationSense.MINIMIZE);
			Expression constraint = new Expression().plus(x, 1.0).plus(y, 1.0);
			model.addConstraint("c1", constraint, Model.ConstraintType.GREATER_EQUAL, 1.0);
			model.setOptionValue("output_flag", false);
			model.setOptionValue("log_to_console", false);
			model.solve();
			model.dispose();
		} catch (Exception e) {
			// Silent
		}
	}
	
	private static double average(long[] values) {
		long sum = 0;
		for (long v : values) sum += v;
		return (double) sum / values.length;
	}
	
	private static double stddev(long[] values, double avg) {
		double sumSq = 0;
		for (long v : values) {
			double diff = v - avg;
			sumSq += diff * diff;
		}
		return Math.sqrt(sumSq / values.length);
	}
	
	private static long min(long[] values) {
		long min = values[0];
		for (long v : values) if (v < min) min = v;
		return min;
	}
	
	private static long max(long[] values) {
		long max = values[0];
		for (long v : values) if (v > max) max = v;
		return max;
	}
	
	public static void main(String[] args) {
		if (args.length > 0 && args[0].equals("--benchmark")) {
			benchmark(args);
		} else {
			// Test the low-level wrapper
			test_highs_wrapper(args);
			
			// Test the high-level model API
			test_highs_model_wrapper(args);
		}
	}
}
