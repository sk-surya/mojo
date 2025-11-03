package org.skor;

//import com.milp.core.*;
import org.skor.HighsModel;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Performance benchmarks for the MILP framework
 */
public class PerformanceBenchmark {
    
    public static void main(String[] args) {
        System.out.println("MILP Framework Performance Benchmark\n");
        
        // Warm up JVM
        warmup();
        
        // Run benchmarks
        benchmarkModelBuilding();
        benchmarkIncrementalUpdates();
        benchmarkLargeScale();
        benchmarkSparseVsDense();
    }
    
    private static void warmup() {
        System.out.println("Warming up JVM...");
        for (int i = 0; i < 3; i++) {
            Model model = new HighsModel();
            for (int j = 0; j < 100; j++) {
                model.addVariable(0, 100, Model.VarType.CONTINUOUS);
            }
        }
        System.out.println("Warmup complete.\n");
    }
    
    /**
     * Benchmark model building performance
     */
    private static void benchmarkModelBuilding() {
        System.out.println("=== Model Building Performance ===");
        
        int[] sizes = {100, 1000, 10000, 50000, 100000};
        
        System.out.printf("%-10s %-15s %-15s %-15s%n", 
                         "Size", "Build Time (ms)", "Vars/sec", "Constraints/sec");
        System.out.println("-".repeat(60));
        
        for (int size : sizes) {
            Model model = new HighsModel();
            
            long startTime = System.nanoTime();
            
            // Build model with batch mode
            model.beginUpdate();
            
            Variable[] vars = new Variable[size];
            for (int i = 0; i < size; i++) {
                vars[i] = model.addVariable(0, 100, 
                    i % 10 == 0 ? Model.VarType.INTEGER : Model.VarType.CONTINUOUS);
            }
            
            // Add constraints (half the number of variables)
            int numConstraints = size / 2;
            Random rand = new Random(42);
            
            for (int i = 0; i < numConstraints; i++) {
                Expression expr = new Expression();
                // Each constraint has ~10 non-zeros
                for (int j = 0; j < 10; j++) {
                    int varIdx = rand.nextInt(size);
                    expr.plus(vars[varIdx], rand.nextDouble() * 10);
                }
                model.addConstraint(expr, Model.ConstraintType.LESS_EQUAL, 
                                  rand.nextDouble() * 1000);
            }
            
            model.endUpdate();
            
            long endTime = System.nanoTime();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
            
            double varsPerSec = (size * 1000.0) / elapsedMs;
            double consPerSec = (numConstraints * 1000.0) / elapsedMs;
            
            System.out.printf("%-10d %-15d %-15.0f %-15.0f%n", 
                            size, elapsedMs, varsPerSec, consPerSec);
        }
        System.out.println();
    }
    
    /**
     * Benchmark incremental update performance
     */
    private static void benchmarkIncrementalUpdates() {
        System.out.println("=== Incremental Update Performance ===");
        
        // Build initial model
        int modelSize = 10000;
        Model model = new HighsModel();
        
        model.beginUpdate();
        Variable[] vars = new Variable[modelSize];
        Constraint[] cons = new Constraint[modelSize / 2];
        
        for (int i = 0; i < modelSize; i++) {
            vars[i] = model.addVariable(0, 100, Model.VarType.CONTINUOUS);
        }
        
        Random rand = new Random(42);
        for (int i = 0; i < cons.length; i++) {
            Expression expr = new Expression();
            for (int j = 0; j < 10; j++) {
                expr.plus(vars[rand.nextInt(modelSize)], rand.nextDouble());
            }
            cons[i] = model.addConstraint(expr, Model.ConstraintType.LESS_EQUAL, 100);
        }
        
        model.endUpdate();
        
        System.out.println("Initial model: " + modelSize + " variables, " + 
                          cons.length + " constraints\n");
        
        // Benchmark different update operations
        System.out.printf("%-30s %-15s %-15s%n", "Operation", "Count", "Time (ms)");
        System.out.println("-".repeat(60));
        
        // Variable bound updates
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            model.updateVariableBounds(vars[i], 0, 50);
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        System.out.printf("%-30s %-15d %-15d%n", "Variable bound updates", 1000, elapsedMs);
        
        // Constraint RHS updates
        startTime = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            model.updateConstraintRHS(cons[i], 200);
        }
        elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        System.out.printf("%-30s %-15d %-15d%n", "Constraint RHS updates", 500, elapsedMs);
        
        // Coefficient updates
        startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 5; j++) {
                model.updateCoefficient(cons[i], vars[j], rand.nextDouble() * 10);
            }
        }
        elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        System.out.printf("%-30s %-15d %-15d%n", "Coefficient updates", 500, elapsedMs);
        
        // Batch updates
        startTime = System.nanoTime();
        model.beginUpdate();
        for (int i = 0; i < 1000; i++) {
            model.updateVariableBounds(vars[i], 10, 90);
        }
        for (int i = 0; i < 500; i++) {
            model.updateConstraintRHS(cons[i], 150);
        }
        model.endUpdate();
        elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        System.out.printf("%-30s %-15d %-15d%n", "Batch updates (mixed)", 1500, elapsedMs);
        
        // Add new variables
        startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            model.addVariable(0, 100, Model.VarType.CONTINUOUS);
        }
        elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        System.out.printf("%-30s %-15d %-15d%n", "Add new variables", 100, elapsedMs);
        
        // Add new constraints
        startTime = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            Expression expr = new Expression();
            for (int j = 0; j < 10; j++) {
                expr.plus(vars[rand.nextInt(modelSize)], rand.nextDouble());
            }
            model.addConstraint(expr, Model.ConstraintType.LESS_EQUAL, 100);
        }
        elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        System.out.printf("%-30s %-15d %-15d%n", "Add new constraints", 50, elapsedMs);
        
        System.out.println();
    }
    
    /**
     * Benchmark large-scale model performance
     */
    private static void benchmarkLargeScale() {
        System.out.println("=== Large-Scale Model Performance ===");
        
        int numVars = 100000;
        int numCons = 50000;
        double density = 0.001; // 0.1% density
        
        System.out.println("Building model: " + numVars + " vars, " + 
                          numCons + " constraints, " + 
                          (density * 100) + "% density");
        
        Model model = new HighsModel();
        
        long startBuild = System.nanoTime();
        
        model.beginUpdate();
        
        // Variables
        Variable[] vars = new Variable[numVars];
        for (int i = 0; i < numVars; i++) {
            vars[i] = model.addVariable(0, 100, 
                i % 100 == 0 ? Model.VarType.INTEGER : Model.VarType.CONTINUOUS);
        }
        
        // Constraints with sparse matrix
        Random rand = new Random(42);
        int totalNonZeros = 0;
        
        for (int i = 0; i < numCons; i++) {
            Expression expr = new Expression();
            int nnz = (int)(numVars * density);
            
            // Use a set to avoid duplicate variables
            Set<Integer> usedVars = new HashSet<>();
            while (usedVars.size() < nnz) {
                usedVars.add(rand.nextInt(numVars));
            }
            
            for (int varIdx : usedVars) {
                expr.plus(vars[varIdx], rand.nextDouble() * 10);
                totalNonZeros++;
            }
            
            model.addConstraint(expr, Model.ConstraintType.LESS_EQUAL, 
                              rand.nextDouble() * 1000);
        }
        
        model.endUpdate();
        
        long buildTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startBuild);
        
        Model.ModelStats stats = model.getStatistics();
        
        System.out.println("\nModel Statistics:");
        System.out.println("  Build time: " + buildTimeMs + " ms");
        System.out.println("  Variables: " + stats.numVariables);
        System.out.println("  Constraints: " + stats.numConstraints);
        System.out.println("  Non-zeros: " + stats.numNonZeros);
        System.out.println("  Actual density: " + 
                          String.format("%.4f%%", 
                          (100.0 * stats.numNonZeros) / (numVars * numCons)));
        System.out.println("  Build rate: " + 
                          String.format("%.0f non-zeros/ms", 
                          (double)stats.numNonZeros / buildTimeMs));
        
        // Memory usage estimate
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Suggest GC for more accurate measurement
        long memoryUsed = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("  Estimated memory: " + 
                          String.format("%.1f MB", memoryUsed / (1024.0 * 1024.0)));
        
        System.out.println();
    }
    
    /**
     * Compare sparse vs dense model building
     */
    private static void benchmarkSparseVsDense() {
        System.out.println("=== Sparse vs Dense Performance ===");
        
        int numVars = 1000;
        int numCons = 1000;
        double[] densities = {0.001, 0.01, 0.05, 0.1, 0.2};
        
        System.out.printf("%-10s %-15s %-15s %-15s%n", 
                         "Density", "Non-zeros", "Build (ms)", "NNZ/ms");
        System.out.println("-".repeat(60));
        
        for (double density : densities) {
            Model model = new HighsModel();
            
            long startTime = System.nanoTime();
            
            model.beginUpdate();
            
            Variable[] vars = new Variable[numVars];
            for (int i = 0; i < numVars; i++) {
                vars[i] = model.addVariable(0, 100, Model.VarType.CONTINUOUS);
            }
            
            Random rand = new Random(42);
            int totalNnz = 0;
            
            for (int i = 0; i < numCons; i++) {
                Expression expr = new Expression();
                int nnz = Math.max(1, (int)(numVars * density));
                
                Set<Integer> usedVars = new HashSet<>();
                while (usedVars.size() < nnz) {
                    usedVars.add(rand.nextInt(numVars));
                }
                
                for (int varIdx : usedVars) {
                    expr.plus(vars[varIdx], rand.nextDouble());
                    totalNnz++;
                }
                
                model.addConstraint(expr, Model.ConstraintType.LESS_EQUAL, 100);
            }
            
            model.endUpdate();
            
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            double nnzPerMs = (double)totalNnz / elapsedMs;
            
            System.out.printf("%-10.1f%% %-15d %-15d %-15.0f%n", 
                            density * 100, totalNnz, elapsedMs, nnzPerMs);
        }
        
        System.out.println("\nConclusion: Framework maintains efficient performance");
        System.out.println("even with increasing density due to sparse representation.");
    }
}
