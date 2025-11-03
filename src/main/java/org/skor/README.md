# MILP Framework

Fast, solver-agnostic Mixed Integer Linear Programming (MILP) framework for Java with full incremental update support.

## Features

- **Solver-agnostic design**: Easy to swap between HiGHS, Gurobi, CPLEX, or other solvers
- **Incremental updates**: Efficiently modify models without rebuilding from scratch
- **Builder pattern API**: Intuitive expression building with method chaining
- **Batch updates**: Group multiple changes for optimal performance
- **Warm start support**: Use previous solutions to accelerate re-solves
- **Large-scale ready**: Optimized for models with millions of variables/constraints
- **Zero-copy where possible**: Direct native solver integration via JNI

## Architecture

```
Your Application
       ↓
   Model API (solver-agnostic)
       ↓
   BaseModel (common logic)
    ↙     ↘
HiGHSModel  GurobiModel (extensible)
    ↓          ↓
highs4j    Gurobi Java API
    ↓          ↓
Native      Native
HiGHS       Gurobi
```

## Quick Start

### Basic LP Example

```java
Model model = new HiGHSModel();

// Variables
Variable x = model.addVariable("x", 0, 10, VarType.CONTINUOUS);
Variable y = model.addVariable("y", 0, 10, VarType.CONTINUOUS);

// Objective: maximize 3x + 2y
model.setObjective(
    Expression.of(x, 3).plus(y, 2),
    OptimizationSense.MAXIMIZE
);

// Constraint: x + y <= 8
model.addConstraint(
    Expression.of(x).plus(y),
    ConstraintType.LESS_EQUAL,
    8
);

// Solve
Solution solution = model.solve();
System.out.println("x = " + solution.getValue(x));
System.out.println("y = " + solution.getValue(y));
```

### Incremental Updates

```java
// Initial model
Model model = new HiGHSModel();
Variable x = model.addVariable(0, 10, VarType.INTEGER);
Constraint con = model.addConstraint(...);

// Solve
Solution sol1 = model.solve();

// Incremental updates
model.beginUpdate();  // Start batch mode
model.updateVariableBounds(x, 0, 20);
model.updateConstraintRHS(con, 15);
model.updateObjectiveCoefficient(x, 5.0);
model.endUpdate();    // Apply all changes

// Re-solve with warm start
Solution sol2 = model.solve(
    new SolverParams().withWarmStart(true)
);
```

### Large-Scale Models

```java
Model model = new HiGHSModel();

// Batch mode for efficient building
model.beginUpdate();

// Add thousands of variables/constraints
for (int i = 0; i < 100000; i++) {
    Variable v = model.addVariable(0, 100, VarType.CONTINUOUS);
    // ...
}

model.endUpdate();  // Build model once

// Solve with parameters
Solution solution = model.solve(
    SolverParams.balanced()
        .withTimeLimit(60)
        .withGapTolerance(0.01)
        .withThreads(8)
);
```

## Performance Characteristics

- **Model Building**: O(n) for n variables/constraints
- **Incremental Variable Update**: O(1)
- **Incremental Constraint Update**: O(nnz) for constraint non-zeros
- **Memory Usage**: Sparse representation throughout
- **JNI Overhead**: Minimal, as operations are batched

## Incremental Update Operations

| Operation | HiGHS Support | Time Complexity |
|-----------|---------------|-----------------|
| Add variable | ✓ | O(1) |
| Update variable bounds | ✓ | O(1) |
| Remove variable | ✓ | O(n) * |
| Add constraint | ✓ | O(nnz) |
| Update constraint RHS | ✓ | O(1) |
| Remove constraint | ✓ | O(m) * |
| Update coefficient | ✓ | O(1) |
| Update objective | ✓ | O(n) |

\* Requires index rebuilding

## Building

```bash
mvn clean package
```

## Dependencies

- Java 25+
- Maven 3.8+

## Adding Other Solvers

To add support for another solver (e.g., Gurobi):

1. Extend `BaseModel`
2. Implement the abstract hook methods
3. Map solver-specific API calls

Example skeleton:

```java
public class GurobiModel extends BaseModel {
    private GRBModel model;
    
    @Override
    protected void onVariableAdded(Variable var) {
        // Add variable to Gurobi model
        GRBVar grbVar = model.addVar(...);
        // Store mapping
    }
    
    // Implement other hooks...
}
```

## License

Unlicense

## Performance Tips

1. **Use batch updates** for multiple changes
2. **Enable warm start** for re-solves
3. **Set appropriate tolerances** based on your needs
4. **Use sparse expressions** - avoid zero coefficients
5. **Reuse model instances** when possible
6. **Profile memory** for very large models (>10M variables)
