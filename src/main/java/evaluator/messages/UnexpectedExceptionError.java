package evaluator.messages;

import evaluator.Tester;
import evaluator.annotations.Test;
import extensions.Extensions;

import java.util.Arrays;

public class UnexpectedExceptionError extends Result {

    private final Tester.MethodCall call;

    private final Object expected;

    private final Throwable exception;

    private final MethodInvocationResult.EqualsType equalsType;

    public UnexpectedExceptionError(Test test, Tester.MethodCall call, Object expected, Throwable exception, MethodInvocationResult.EqualsType equalsType) {
        super(test);

        if (equalsType == MethodInvocationResult.EqualsType.CONTENT || equalsType == MethodInvocationResult.EqualsType.PERMUTATION || equalsType == MethodInvocationResult.EqualsType.ANY) {
            if (expected != null && !expected.getClass().isArray() && !Iterable.class.isAssignableFrom(expected.getClass()))
                throw new IllegalArgumentException("Expected value should be an array or Iterable collection of elements, but is " + expected.getClass() + "!");
        }

        this.call = call;
        this.expected = expected;
        this.exception = exception;
        this.equalsType = equalsType;
    }

    @Override
    public String errorCode() {
        return "Unexpected Exception";
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
            case EXACT -> "Expected <" + Extensions.toStringOrDefault(expected) + ">";
            case ANY -> "Expected one of " + Extensions.toStringOrDefault(expected);
            case PERMUTATION -> "Expected a permutation of <" + Extensions.toStringOrDefault(expected) + ">";
            case CONTENT -> "Expected content to be <" + Extensions.toStringOrDefault(expected) + ">";
        };

        return call + " returned wrong result: " +
                expectedMessage + " but threw " + exception.getClass().getSimpleName() + " " +
                "with message \"" + exception.getMessage() + "\"";
    }
}
