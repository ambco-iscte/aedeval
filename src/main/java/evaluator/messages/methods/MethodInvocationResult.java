package evaluator.messages.methods;

import evaluator.AssertEqualsType;
import evaluator.Tester;
import evaluator.annotations.Test;
import evaluator.messages.Result;
import extensions.Extensions;

public class MethodInvocationResult extends Result {

    private final Tester.MethodCall call;
    private final Object expected;
    private final Object actual;
    private final AssertEqualsType equalsType;

    public MethodInvocationResult(Test test, Tester.MethodCall call, Object expected, Object actual, AssertEqualsType equalsType) {
        super(test);

        if (equalsType == AssertEqualsType.CONTENT || equalsType == AssertEqualsType.PERMUTATION || equalsType == AssertEqualsType.ANY) {
            if (expected != null && !expected.getClass().isArray() && !Iterable.class.isAssignableFrom(expected.getClass()))
                throw new IllegalArgumentException("Expected value should be an array or Iterable collection of elements, but is " + expected.getClass().getCanonicalName() + "!");
        }

        if (equalsType == AssertEqualsType.TYPEOF && !Class.class.isAssignableFrom(expected.getClass()))
            throw new IllegalArgumentException("Expected value should be a java.lang.Class, but is " + expected.getClass().getCanonicalName() + "!");

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
        return equalsType.equals(expected, actual);
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
        String expectedMessage = switch (equalsType) {
            case EXACT -> "expected <" + Extensions.toStringOrDefault(expected) + ">";
            case ANY -> "expected one of " + Extensions.toStringOrDefault(expected);
            case PERMUTATION -> "expected a permutation of <" + Extensions.toStringOrDefault(expected) + ">";
            case CONTENT -> "expected content to be <" + Extensions.toStringOrDefault(expected) + ">";
            case NOTNULL -> "expected a non-null value";
            case TYPEOF -> "expected a value of type " + ((Class<?>) expected).getCanonicalName();
        };

        if (passed())
            return Extensions.capitaliseFirstCharacter(expectedMessage);

        return "%s returned wrong result: %s but was <%s>".formatted(
            call.toStringWithHistoryOrDefault(),
            expectedMessage,
            Extensions.toStringOrDefault(actual)
        );
    }
}
