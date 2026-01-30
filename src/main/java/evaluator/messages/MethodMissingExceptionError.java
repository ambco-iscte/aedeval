package evaluator.messages;

import evaluator.Tester;
import evaluator.annotations.Test;

public class MethodMissingExceptionError<T extends Throwable> extends Result {

    private final Tester.MethodCall call;

    private final Class<T> expected;

    private final Object actual;

    public MethodMissingExceptionError(Test test, Tester.MethodCall call, Class<T> expected, Object actual) {
        super(test);
        this.call = call;
        this.expected = expected;
        this.actual = actual;
    }

    @Override
    public String errorCode() {
        return "Missing Required Exception";
    }

    public Tester.MethodCall getMethodCall() {
        return call;
    }

    public Class<T> getExpected() {
        return expected;
    }

    public Object getActual() {
        return actual;
    }

    @Override
    public boolean passed() {
        return false;
    }

    @Override
    public String getMessage() {
        return call + " returned <" + actual + "> but should have thrown " + expected.getSimpleName();
    }
}
