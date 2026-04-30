package evaluator.algorithmic.functions.composite;

import evaluator.algorithmic.functions.SymbolicFunction;
import evaluator.algorithmic.functions.simple.Constant;
import evaluator.algorithmic.functions.simple.Variable;

import java.util.Map;
import java.util.Set;

public class Logarithm extends SymbolicFunction {

    private final SymbolicFunction base;
    private final SymbolicFunction argument;

    public Logarithm(SymbolicFunction base, SymbolicFunction argument) {
        if (base == null || argument == null)
            throw new IllegalArgumentException("");
        this.base = base;
        this.argument = argument;
    }

    public SymbolicFunction base() {
        return base;
    }

    public SymbolicFunction argument() {
        return argument;
    }

    @Override
    protected double evaluate(Map<String, Double> context) {
        return Math.log(argument.apply(context)) / Math.log(base.apply(context));
    }

    @Override
    public Set<Variable> variables() {
        Set<Variable> variables = base.variables();
        variables.addAll(argument.variables());
        return variables;
    }

    @Override
    public SymbolicFunction simplified() {
        SymbolicFunction inner = argument.simplified();

        if (inner.equals(Constant.ONE))
            return Constant.ZERO;

        if (inner instanceof Power power && power.base().simplified().equals(base))
            return power.right().simplified();

        return new Logarithm(base.simplified(), inner);
    }

    @Override
    public String toString() {
        return String.format("log_%s(%s)", base, argument);
    }

    public static Logarithm log10(SymbolicFunction argument) {
        return new Logarithm(new Constant(10.0), argument) {
            @Override
            public String toString() {
                return String.format("log(%s)", argument);
            }
        };
    }

    public static Logarithm log2(SymbolicFunction argument) {
        return new Logarithm(new Constant(2.0), argument) {
            @Override
            public String toString() {
                return String.format("lg(%s)", argument);
            }
        };
    }
}
