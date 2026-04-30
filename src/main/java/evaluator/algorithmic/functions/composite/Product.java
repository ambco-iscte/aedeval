package evaluator.algorithmic.functions.composite;

import evaluator.algorithmic.functions.SymbolicFunction;
import evaluator.algorithmic.functions.simple.Constant;

import java.util.Map;

public final class Product extends CompositeFunction {

    @Override
    protected int precedence() { // PEMDAS
        return 3;
    }

    @Override
    protected String operator() {
        return "*";
    }

    public Product(SymbolicFunction left, SymbolicFunction right) {
        super(left, right);
    }

    @Override
    protected double evaluate(Map<String, Double> context) {
        return left().apply(context) * right().apply(context);
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

        if (left.equals(Constant.ZERO) || right.equals(Constant.ZERO))
            return Constant.ZERO;

        if (right.equals(Constant.ONE))
            return left;
        if (left.equals(Constant.ONE))
            return right;

        if (left instanceof Constant c1 && right instanceof Constant c2)
            return new Constant(c1.value() * c2.value());

        if (left instanceof Constant c1 && right instanceof Product s) {
            if (s.left().simplified() instanceof Constant c2)
                return new Product(new Constant(c1.value() * c2.value()), s.right().simplified());
            if (s.right().simplified() instanceof Constant c2)
                return new Product(new Constant(c1.value() * c2.value()), s.left().simplified());
        }

        if (right instanceof Constant c1 && left instanceof Product s) {
            if (s.left().simplified() instanceof Constant c2)
                return new Product(new Constant(c1.value() * c2.value()), s.right().simplified());
            if (s.right().simplified() instanceof Constant c2)
                return new Product(new Constant(c1.value() * c2.value()), s.left().simplified());
        }

        return new Product(left, right);
    }
}
