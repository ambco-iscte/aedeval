package evaluator.algorithmic.functions.composite;

import evaluator.algorithmic.functions.SymbolicFunction;
import evaluator.algorithmic.functions.simple.Constant;

import java.util.Map;

public final class Power extends CompositeFunction {

    @Override
    protected int precedence() { // PEMDAS
        return 4;
    }

    @Override
    protected String operator() {
        return "^";
    }

    public Power(SymbolicFunction base, SymbolicFunction exponent) {
        super(base, exponent);
    }

    public SymbolicFunction base() {
        return left();
    }

    public SymbolicFunction exponent() {
        return right();
    }

    @Override
    protected double evaluate(Map<String, Double> context) {
        return Math.pow(left().apply(context), right().apply(context));
    }

    @Override
    public SymbolicFunction simplified() {
        SymbolicFunction base = left().simplified();
        SymbolicFunction exponent = right().simplified();

        if (base.equals(Constant.ZERO) || base.equals(Constant.ONE) || exponent.equals(Constant.ONE))
            return base;

        if (base instanceof Constant c1 && exponent instanceof Constant c2)
            return new Constant(Math.pow(c1.value(), c2.value()));

        if (exponent instanceof Logarithm log && log.base().simplified().equals(base))
            return log.argument().simplified();

        return new Power(base, exponent);
    }
}
