package evaluator.messages;

import evaluator.Tester;
import evaluator.annotations.Test;

import java.util.Arrays;

public class UnexpectedExceptionError extends Result {

    private final Tester.MethodCall call;

    private final Object expected;

    private final Throwable exception;

    private final boolean expectOneOf;

    public UnexpectedExceptionError(Test test, Tester.MethodCall call, Object expected, Throwable exception, boolean expectOneOf) {
        super(test);

        if (expectOneOf && !(expected instanceof Object[]))
            throw new IllegalArgumentException("Can only expect 'one of' for expected Object arrays!");

        this.call = call;
        this.expected = expected;
        this.exception = exception;
        this.expectOneOf = expectOneOf;
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
        if (expectOneOf)
            return call + " returned wrong result: " +
                    "Expected one of " + Arrays.toString((Object[]) expected) + " but threw " + exception.getClass().getSimpleName() + " " +
                    "with message \"" + exception.getMessage() + "\"";
        return call + " returned wrong result: " +
                "Expected <" + expected + "> but threw " + exception.getClass().getSimpleName() + " " +
                "with message \"" + exception.getMessage() + "\"";
    }
}
