package evaluator.algorithmic.functions.simple;

import evaluator.algorithmic.functions.SymbolicFunction;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Constant extends SymbolicFunction {

    public static final Constant ZERO = new Constant(0.0);
    public static final Constant ONE = new Constant(1.0);
    public static final Constant E = new Constant(Math.E);
    public static final Constant PI = new Constant(Math.PI);

    private final double value;

    public Constant(double value) {
        this.value = value;
    }

    public double value() {
        return value;
    }

    @Override
    public double evaluate(Map<String, Double> context) {
        return value;
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
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Constant c && c.value == value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
