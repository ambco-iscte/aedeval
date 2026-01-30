package evaluator.messages;

import evaluator.Tester;
import evaluator.annotations.Test;

public class MethodTimeoutError extends Result {

    private final Tester.MethodCall call;

    public MethodTimeoutError(Test test, Tester.MethodCall call) {
        super(test);
        this.call = call;
    }

    @Override
    public String errorCode() {
        return "Method Timeout";
    }

    public Tester.MethodCall getMethodCall() {
        return call;
    }

    @Override
    public boolean passed() {
        return false;
    }

    @Override
    public String getMessage() {
        return "Method execution timed out: " + call + ". Have you checked for infinite loops or unbounded recursion?";
    }
}
