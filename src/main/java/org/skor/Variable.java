package org.skor;

import java.util.Objects;

/**
 * Represents a decision variable in the model
 */
public class Variable {
    private final int index;
    private final String name;
    private double lowerBound;
    private double upperBound;
    private final Model.VarType type;
    
    public Variable(int index, String name, double lowerBound, double upperBound, Model.VarType type) {
        this.index = index;
        this.name = name;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.type = type;
    }
    
    public int getIndex() { return index; }
    public String getName() { return name; }
    public double getLowerBound() { return lowerBound; }
    public double getUpperBound() { return upperBound; }
    public Model.VarType getType() { return type; }
    
    void setLowerBound(double lb) { this.lowerBound = lb; }
    void setUpperBound(double ub) { this.upperBound = ub; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Variable)) return false;
        Variable variable = (Variable) o;
        return index == variable.index;
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
