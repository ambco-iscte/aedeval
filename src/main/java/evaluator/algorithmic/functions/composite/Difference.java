package evaluator.algorithmic.functions.composite;

import evaluator.algorithmic.functions.SymbolicFunction;
import evaluator.algorithmic.functions.simple.Constant;

import java.util.Map;

public final class Difference extends CompositeFunction {

    @Override
    protected int precedence() { // PEMDAS
        return 0;
    }

    @Override
    protected String operator() {
        return "-";
    }

    public Difference(SymbolicFunction left, SymbolicFunction right) {
        super(left, right);
    }

    @Override
    protected double evaluate(Map<String, Double> context) {
        return left().apply(context) - right().apply(context);
    }

    public SymbolicFunction first() {
        return left();
    }

    public SymbolicFunction second() {
        return right();
    }

    @Override
    public SymbolicFunction simplified() {
        SymbolicFunction left = left().simplified();
        SymbolicFunction right = right().simplified();

        if (right.equals(Constant.ZERO))
            return left;

        if (left instanceof Constant c1 && right instanceof Constant c2)
            return new Constant(c1.value() - c2.value());

        return new Difference(left, right);
    }
}
