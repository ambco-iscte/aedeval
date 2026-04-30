package evaluator.algorithmic.functions.composite;

import evaluator.algorithmic.functions.SymbolicFunction;
import evaluator.algorithmic.functions.simple.Variable;

import java.util.Objects;
import java.util.Set;

public abstract class CompositeFunction extends SymbolicFunction {

    private final SymbolicFunction left;
    private final SymbolicFunction right;

    protected abstract String operator();
    protected abstract int precedence();

    public CompositeFunction(SymbolicFunction left, SymbolicFunction right) {
        if (left == null || right == null)
            throw new IllegalArgumentException("");

        this.left = left;
        this.right = right;
    }

    protected SymbolicFunction left() {
        return left;
    }

    protected SymbolicFunction right() {
        return right;
    }

    @Override
    public Set<Variable> variables() {
        Set<Variable> variables = left.variables();
        variables.addAll(right.variables());
        return variables;
    }

    @Override
    public String toString() {
        String first = left().toString();
        if (left() instanceof CompositeFunction f && this.precedence() > f.precedence())
            first = "(%s)".formatted(first);

        String second = right().toString();
        if (right() instanceof CompositeFunction f && this.precedence() > f.precedence())
            second = "(%s)".formatted(second);

        return String.format("%s %s %s", first, operator(), second);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CompositeFunction o &&
                precedence() == o.precedence() &&
                Objects.equals(operator(), o.operator()) &&
                Objects.equals(left, o.left) &&
                Objects.equals(right, o.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(precedence(), operator(), left, right);
    }
}
