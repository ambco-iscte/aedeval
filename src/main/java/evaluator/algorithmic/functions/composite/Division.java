package evaluator.algorithmic.functions.composite;

import evaluator.algorithmic.functions.SymbolicFunction;
import evaluator.algorithmic.functions.simple.Constant;

import java.util.Map;

public final class Division extends CompositeFunction {

    @Override
    protected int precedence() { // PEMDAS
        return 2;
    }

    @Override
    protected String operator() {
        return "/";
    }

    public Division(SymbolicFunction numerator, SymbolicFunction denominator) {
        super(numerator, denominator);
    }

    public SymbolicFunction numerator() {
        return left();
    }

    public SymbolicFunction denominator() {
        return right();
    }

    @Override
    protected double evaluate(Map<String, Double> context) {
        return left().apply(context) / right().apply(context);
    }

    @Override
    public SymbolicFunction simplified() {
        SymbolicFunction left = left().simplified();
        SymbolicFunction right = right().simplified();

        if (left.equals(Constant.ZERO))
            return Constant.ZERO;
        if (right.equals(Constant.ONE))
            return left;

        if (left instanceof Constant c1 && right instanceof Constant c2)
            return new Constant(c1.value() / c2.value());

        return new Division(left, right);
    }
}
