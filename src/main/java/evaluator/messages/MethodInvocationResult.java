package evaluator.messages;

import evaluator.Tester;
import evaluator.annotations.Test;
import extensions.Extensions;

import java.util.*;

public class MethodInvocationResult extends Result {

    public enum EqualsType {
        EXACT,          // Result should equal this exact value
        ANY,            // Result should equal any of the values in an array/Iterable
        PERMUTATION,    // Result should be a permutation of the given array/Iterable
        CONTENT         // Result should be an array/Iterable with the same content as the given array/Iterable
    }

    private final Tester.MethodCall call;
    private final Object expected;
    private final Object actual;
    private final EqualsType equalsType;

    public MethodInvocationResult(Test test, Tester.MethodCall call, Object expected, Object actual, EqualsType equalsType) {
        super(test);

        if (equalsType == EqualsType.CONTENT || equalsType == EqualsType.PERMUTATION || equalsType == EqualsType.ANY) {
            if (expected != null && !expected.getClass().isArray() && !Iterable.class.isAssignableFrom(expected.getClass()))
                throw new IllegalArgumentException("Expected value should be an array or Iterable collection of elements, but is " + expected.getClass() + "!");
        }

        this.call = call;
        this.expected = expected;
        this.actual = actual;
        this.equalsType = equalsType;
    }

    @Override
    public String errorCode() {
        return "Wrong Method Result";
    }

    public boolean passed() {
        switch (equalsType) {
            case EXACT -> {
                return Objects.equals(expected, actual);
            }

            case ANY -> {
                Object[] exp = Extensions.toArray(expected);
                return Arrays.asList(exp).contains(actual);
            }

            case PERMUTATION -> {
                if (actual == null)
                    return expected == null;
                if (!actual.getClass().isArray() && !Iterable.class.isAssignableFrom(actual.getClass()))
                    return false;

                List<Object> exp = Arrays.asList(Extensions.toArray(expected));
                List<Object> act = Arrays.asList(Extensions.toArray(actual));
                return exp.size() == act.size() && new HashSet<>(exp).equals(new HashSet<>(act));
            }

            case CONTENT -> {
                if (actual == null)
                    return expected == null;
                if (!actual.getClass().isArray() && !Iterable.class.isAssignableFrom(actual.getClass()))
                    return false;

                return Arrays.equals(Extensions.toArray(expected), Extensions.toArray(actual));
            }
        }
        return false;
    }

    public Tester.MethodCall getMethodCall() {
        return call;
    }

    public Object expected() {
        return expected;
    }

    public Object actual() {
        return actual;
    }

    @Override
    public String getMessage() {
        String expectedMessage = switch(equalsType) {
            case EXACT -> "Expected <" + Extensions.toStringOrDefault(expected) + ">";
            case ANY -> "Expected one of " + Extensions.toStringOrDefault(expected);
            case PERMUTATION -> "Expected a permutation of <" + Extensions.toStringOrDefault(expected) + ">";
            case CONTENT -> "Expected content to be <" + Extensions.toStringOrDefault(expected) + ">";
        };

        if (passed())
            return expectedMessage;
        return call + " returned wrong result: " + expectedMessage + " but was <" + Extensions.toStringOrDefault(actual) + ">";
    }
}
