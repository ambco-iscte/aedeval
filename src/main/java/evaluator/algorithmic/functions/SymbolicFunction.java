package evaluator.algorithmic.functions;

import evaluator.algorithmic.functions.composite.*;
import evaluator.algorithmic.functions.simple.Variable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public abstract class SymbolicFunction {

    protected abstract double evaluate(Map<String, Double> context);

    public abstract Set<Variable> variables();

    public abstract SymbolicFunction simplified();

    public final double apply(Map<String, Double> context) {
        if (context.size() < arity())
            throw new IllegalArgumentException("");
        return evaluate(context);
    }

    public final int arity() {
        return variables().size();
    }

    final double evaluate() {
        return evaluate(Collections.emptyMap());
    }

    public final SymbolicFunction plus(SymbolicFunction other) {
        return new Summation(this, other);
    }

    public final SymbolicFunction minus(SymbolicFunction other) {
        return new Difference(this, other);
    }

    public final SymbolicFunction product(SymbolicFunction other) {
        return new Product(this, other);
    }

    public final SymbolicFunction div(SymbolicFunction other) {
        return new Division(this, other);
    }

    public final SymbolicFunction pow(SymbolicFunction other) {
        return new Power(this, other);
    }
}




