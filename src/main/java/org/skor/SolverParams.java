package org.skor;

import java.util.HashMap;
import java.util.Map;

/**
 * Solver parameters that are common across different solvers
 */
public class SolverParams {
    private double timeLimit = Double.POSITIVE_INFINITY;
    private double gapTolerance = 1e-4;
    private double feasibilityTolerance = 1e-7;
    private double optimalityTolerance = 1e-7;
    private int threads = 0; // 0 = auto
    private int iterationLimit = Integer.MAX_VALUE;
    private int nodeLimit = Integer.MAX_VALUE;
    private int solutionLimit = Integer.MAX_VALUE;
    private String presolve = "auto";		// "on", "off", "auto"
    private int outputLevel = 1; // 0 = silent, 1 = normal, 2 = verbose
    private boolean warmStart = false;
    private Map<String, Object> solverSpecific = new HashMap<>();
    
    // Fluent setters
    public SolverParams withTimeLimit(double seconds) {
        this.timeLimit = seconds;
        return this;
    }
    
    public SolverParams withGapTolerance(double gap) {
        this.gapTolerance = gap;
        return this;
    }
    
    public SolverParams withFeasibilityTolerance(double tol) {
        this.feasibilityTolerance = tol;
        return this;
    }
    
    public SolverParams withOptimalityTolerance(double tol) {
        this.optimalityTolerance = tol;
        return this;
    }
    
    public SolverParams withThreads(int threads) {
        this.threads = threads;
        return this;
    }
    
    public SolverParams withIterationLimit(int limit) {
        this.iterationLimit = limit;
        return this;
    }
    
    public SolverParams withNodeLimit(int limit) {
        this.nodeLimit = limit;
        return this;
    }
    
    public SolverParams withSolutionLimit(int limit) {
        this.solutionLimit = limit;
        return this;
    }

    public SolverParams withPresolve(String mode) {
		mode = mode.toLowerCase();
        if (!mode.equals("on") && !mode.equals("off") && !mode.equals("auto")) {
            throw new IllegalArgumentException("Invalid presolve mode: " + mode);
        }
        this.presolve = mode;
        return this;
    }
    
    public SolverParams withOutputLevel(int level) {
        this.outputLevel = level;
        return this;
    }
    
    public SolverParams withWarmStart(boolean enabled) {
        this.warmStart = enabled;
        return this;
    }
    
    public SolverParams withSolverSpecific(String key, Object value) {
        this.solverSpecific.put(key, value);
        return this;
    }
    
    // Getters
    public double getTimeLimit() { return timeLimit; }
    public double getGapTolerance() { return gapTolerance; }
    public double getFeasibilityTolerance() { return feasibilityTolerance; }
    public double getOptimalityTolerance() { return optimalityTolerance; }
    public int getThreads() { return threads; }
    public int getIterationLimit() { return iterationLimit; }
    public int getNodeLimit() { return nodeLimit; }
    public int getSolutionLimit() { return solutionLimit; }
    public String getPresolve() { return presolve; }
    public int getOutputLevel() { return outputLevel; }
    public boolean isWarmStart() { return warmStart; }
    public Map<String, Object> getSolverSpecific() { return solverSpecific; }
    
    // Common presets
    public static SolverParams quick() {
        return new SolverParams()
            .withTimeLimit(60)
            .withGapTolerance(0.01)
            .withPresolve("auto")
            .withThreads(0);
    }
    
    public static SolverParams balanced() {
        return new SolverParams()
            .withTimeLimit(300)
            .withGapTolerance(0.001)
            .withPresolve("auto")
            .withThreads(0);
    }
    
    public static SolverParams exact() {
        return new SolverParams()
            .withGapTolerance(1e-9)
            .withFeasibilityTolerance(1e-9)
            .withOptimalityTolerance(1e-9)
            .withPresolve("auto")
            .withThreads(0);
    }
}
