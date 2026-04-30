package evaluator.algorithmic.functions.simple;

import evaluator.algorithmic.functions.SymbolicFunction;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Variable extends SymbolicFunction {

    private final String identifier;

    public Variable(String identifier) {
        if (identifier == null)
            throw new IllegalArgumentException("");
        this.identifier = identifier;
    }

    @Override
    protected double evaluate(Map<String, Double> context) {
        return context.get(identifier);
    }

    @Override
    public Set<Variable> variables() {
        return Set.of();
    }

    @Override
    public SymbolicFunction simplified() {
        return this;
    }

    @Override
    public String toString() {
        return identifier;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Variable v && Objects.equals(v.identifier, identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }
}
