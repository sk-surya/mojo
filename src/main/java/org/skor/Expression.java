package org.skor;

import java.util.*;

/**
 * Builder for linear expressions with efficient sparse representation
 */
public class Expression {
    private final Map<Variable, Double> terms;
    private double constant;
    
    public Expression() {
        this.terms = new HashMap<>();
        this.constant = 0.0;
    }
    
    public Expression(Expression other) {
        this.terms = new HashMap<>(other.terms);
        this.constant = other.constant;
    }
    
    // Builder methods
    public Expression plus(Variable var, double coeff) {
        if (Math.abs(coeff) > 1e-10) { // ignore near-zero coefficients
            terms.merge(var, coeff, Double::sum);
            if (Math.abs(terms.get(var)) < 1e-10) {
                terms.remove(var); // remove if coefficient becomes zero
            }
        }
        return this;
    }
    
    public Expression plus(Variable var) {
        return plus(var, 1.0);
    }
    
    public Expression minus(Variable var, double coeff) {
        return plus(var, -coeff);
    }
    
    public Expression minus(Variable var) {
        return plus(var, -1.0);
    }
    
    public Expression plus(Expression other) {
        other.terms.forEach((var, coeff) -> plus(var, coeff));
        this.constant += other.constant;
        return this;
    }
    
    public Expression minus(Expression other) {
        other.terms.forEach((var, coeff) -> plus(var, -coeff));
        this.constant -= other.constant;
        return this;
    }
    
    public Expression times(double multiplier) {
        Map<Variable, Double> newTerms = new HashMap<>();
        terms.forEach((var, coeff) -> {
            double newCoeff = coeff * multiplier;
            if (Math.abs(newCoeff) > 1e-10) {
                newTerms.put(var, newCoeff);
            }
        });
        terms.clear();
        terms.putAll(newTerms);
        constant *= multiplier;
        return this;
    }
    
    public Expression plusConstant(double value) {
        this.constant += value;
        return this;
    }
    
    // Static factory methods for convenience
    public static Expression of(Variable var) {
        return new Expression().plus(var);
    }
    
    public static Expression of(Variable var, double coeff) {
        return new Expression().plus(var, coeff);
    }
    
    public static Expression constant(double value) {
        return new Expression().plusConstant(value);
    }
    
    public static Expression sum(Variable... vars) {
        Expression expr = new Expression();
        for (Variable var : vars) {
            expr.plus(var);
        }
        return expr;
    }
    
    public static Expression weightedSum(Map<Variable, Double> terms) {
        Expression expr = new Expression();
        terms.forEach(expr::plus);
        return expr;
    }
    
    // Accessors
    public Map<Variable, Double> getTerms() {
        return Collections.unmodifiableMap(terms);
    }
    
    public double getConstant() {
        return constant;
    }
    
    public double getCoefficient(Variable var) {
        return terms.getOrDefault(var, 0.0);
    }
    
    public int size() {
        return terms.size();
    }
    
    public boolean isEmpty() {
        return terms.isEmpty() && Math.abs(constant) < 1e-10;
    }
    
    public Set<Variable> getVariables() {
        return Collections.unmodifiableSet(terms.keySet());
    }
    
    // For efficient iteration
    public void forEachTerm(TermConsumer consumer) {
        terms.forEach(consumer::accept);
    }
    
    @FunctionalInterface
    public interface TermConsumer {
        void accept(Variable var, double coeff);
    }
    
    @Override
    public String toString() {
        if (isEmpty()) return "0";
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        
        for (Map.Entry<Variable, Double> entry : terms.entrySet()) {
            double coeff = entry.getValue();
            if (Math.abs(coeff) < 1e-10) continue;
            
            if (!first) {
                sb.append(coeff >= 0 ? " + " : " - ");
                coeff = Math.abs(coeff);
            } else if (coeff < 0) {
                sb.append("-");
                coeff = Math.abs(coeff);
            }
            
            if (Math.abs(coeff - 1.0) > 1e-10) {
                sb.append(coeff).append("*");
            }
            sb.append(entry.getKey().getName());
            first = false;
        }
        
        if (Math.abs(constant) > 1e-10) {
            if (!first) {
                sb.append(constant >= 0 ? " + " : " - ");
                sb.append(Math.abs(constant));
            } else {
                sb.append(constant);
            }
        }
        
        return sb.toString();
    }
}
