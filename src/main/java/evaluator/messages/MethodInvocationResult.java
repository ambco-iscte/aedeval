package evaluator.messages;

import evaluator.Tester;
import evaluator.annotations.Test;
import extensions.Extensions;

import java.util.*;

public class MethodInvocationResult extends Result {

    public enum ExpectedType {
        EXACT,
        ANY,
        PERMUTATION
    }

    private final Tester.MethodCall call;
    private final Object expected;
    private final Object actual;
    private final ExpectedType expectedType;

    public MethodInvocationResult(Test test, Tester.MethodCall call, Object expected, Object actual, ExpectedType expectedType) {
        super(test);

        if (expectedType == ExpectedType.PERMUTATION || expectedType == ExpectedType.ANY) {
            if (expected != null && !expected.getClass().isArray() && !Collection.class.isAssignableFrom(expected.getClass()))
                throw new IllegalArgumentException("Expected value should be a collection of elements, but is " + expected.getClass() + "!");
        }

        this.call = call;
        this.expected = expected;
        this.actual = actual;
        this.expectedType = expectedType;
    }

    @Override
    public String errorCode() {
        return "Wrong Method Result";
    }

    public boolean passed() {
        switch (expectedType) {
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
                return exp.size() == act.size() && exp.containsAll(act) && act.containsAll(exp);
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
        switch (expectedType) {
            case ANY -> {
                if (passed())
                    return "Expected one of " + Extensions.toStringOrDefault(expected);
                return call + " returned wrong result: Expected one of " +
                        Extensions.toStringOrDefault(expected) + " but was <" + Extensions.toStringOrDefault(actual) + ">";
            }

            case PERMUTATION -> {
                if (passed())
                    return "Expected a permutation of " + Extensions.toStringOrDefault(expected);
                return call + " returned wrong result: Expected a permutation of <" +
                        Extensions.toStringOrDefault(expected) + "> but was <" + Extensions.toStringOrDefault(actual) + ">";
            }
        }
        if (passed())
            return "Expected <" + Extensions.toStringOrDefault(expected) + ">";
        return call + " returned wrong result: Expected <" + Extensions.toStringOrDefault(expected) + "> but was <" +
                Extensions.toStringOrDefault(actual) + ">";
    }
}
