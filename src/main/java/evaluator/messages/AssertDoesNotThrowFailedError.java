package evaluator.messages;

import evaluator.Tester;
import evaluator.annotations.Test;

public class AssertDoesNotThrowFailedError extends Result {

    private final Tester.MethodCall call;

    private final Throwable exception;

    public AssertDoesNotThrowFailedError(Test test, Tester.MethodCall call, Throwable exception) {
        super(test);
        this.call = call;
        this.exception = exception;
    }

    public Tester.MethodCall getMethodCall() {
        return call;
    }

    public Throwable getException() {
        return exception;
    }

    @Override
    public String errorCode() {
        return "Unexpected Exception";
    }

    @Override
    public boolean passed() {
        return false;
    }

    @Override
    public String getMessage() {
        return call + " should not have thrown any exceptions, but threw " + exception.getClass().getSimpleName() + " " +
                "with message \"" + exception.getMessage() + "\"";
    }
}
