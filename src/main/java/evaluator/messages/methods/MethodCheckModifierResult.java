package evaluator.messages.methods;

import evaluator.annotations.Test;
import evaluator.messages.Result;
import extensions.Extensions;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MethodCheckModifierResult extends Result {

    private final Method method;
    private final int expected;

    public MethodCheckModifierResult(Test test, Method method, int expected) {
        super(test);

        if ((expected & Modifier.methodModifiers()) == 0)
            throw new IllegalArgumentException("Value " + expected + " does not correspond to a valid method modifier!");

        this.method = method;
        this.expected = expected;
    }

    @Override
    public String errorCode() {
        return "Wrong Method Modifier";
    }

    @Override
    public boolean passed() {
        return (method.getModifiers() & expected) != 0;
    }

    @Override
    public String getMessage() {
        return "Expected method <%s> to be <%s>, but its modifiers are <%s>.".formatted(
                Extensions.signature(method),
                Modifier.toString(expected),
                Modifier.toString(method.getModifiers())
        );
    }
}
