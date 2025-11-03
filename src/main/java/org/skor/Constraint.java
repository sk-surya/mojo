package org.skor;

import java.util.Objects;

/**
 * Represents a constraint in the model
 */
public class Constraint {
    private final int index;
    private final String name;
    private Model.ConstraintType type;
    private double rhs;
    private double lhs; // for range constraints
    
    public Constraint(int index, String name, Model.ConstraintType type, double rhs) {
        this.index = index;
        this.name = name;
        this.type = type;
        this.rhs = rhs;
        this.lhs = Double.NEGATIVE_INFINITY;
    }
    
    public Constraint(int index, String name, double lhs, double rhs) {
        this.index = index;
        this.name = name;
        this.type = null; // indicates range constraint
        this.lhs = lhs;
        this.rhs = rhs;
    }
    
    public int getIndex() { return index; }
    public String getName() { return name; }
    public Model.ConstraintType getType() { return type; }
    public double getRhs() { return rhs; }
    public double getLhs() { return lhs; }
    public boolean isRange() { return type == null; }
    
    void setRhs(double rhs) { this.rhs = rhs; }
    void setLhs(double lhs) { this.lhs = lhs; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Constraint)) return false;
        Constraint that = (Constraint) o;
        return index == that.index;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(index);
    }
    
    @Override
    public String toString() {
        return name;
    }
}
