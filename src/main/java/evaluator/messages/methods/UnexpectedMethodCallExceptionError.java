package evaluator.messages.methods;

import evaluator.AssertEqualsType;
import evaluator.Tester;
import evaluator.annotations.Test;
import evaluator.messages.Result;
import extensions.Extensions;

public class UnexpectedMethodCallExceptionError extends Result {

    private final Tester.MethodCall call;
    private final Object expected;
    private final Throwable exception;
    private final AssertEqualsType equalsType;

    public UnexpectedMethodCallExceptionError(Test test, Tester.MethodCall call, Object expected, Throwable exception, AssertEqualsType equalsType) {
        super(test);

        if (equalsType == AssertEqualsType.CONTENT || equalsType == AssertEqualsType.PERMUTATION || equalsType == AssertEqualsType.ANY) {
            if (expected != null && !expected.getClass().isArray() && !Iterable.class.isAssignableFrom(expected.getClass()))
                throw new IllegalArgumentException("Expected value should be an array or Iterable collection of elements, but is " + expected.getClass() + "!");
        }

        if (equalsType == AssertEqualsType.TYPEOF && !Class.class.isAssignableFrom(expected.getClass()))
            throw new IllegalArgumentException("Expected value should be a java.lang.Class, but is " + expected.getClass().getCanonicalName() + "!");

        this.call = call;
        this.expected = expected;
        this.exception = exception;
        this.equalsType = equalsType;
    }

    @Override
    public String errorCode() {
        return "Unexpected Method Exception";
    }

    public Tester.MethodCall getMethodCall() {
        return call;
    }

    public Object getExpected() {
        return expected;
    }

    public Throwable getException() {
        return exception;
    }

    @Override
    public boolean passed() {
        return false;
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

        return "%s returned wrong result: %s but threw an unexpected %s%s".formatted(
            call.toStringWithHistoryOrDefault(),
            expectedMessage,
            exception.getClass().getSimpleName(),
            exception.getMessage() == null ? "" : ": " + exception.getMessage()
        );
    }
}
