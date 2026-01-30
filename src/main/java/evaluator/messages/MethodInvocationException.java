package evaluator.messages;

import evaluator.Tester;
import evaluator.annotations.Test;

public class MethodInvocationException<T extends Throwable, R extends Throwable> extends Result {

    private final Tester.MethodCall call;

    private final Class<T> expected;

    private final Class<R> actual;

    public MethodInvocationException(Test test, Tester.MethodCall call, Class<T> expected, Class<R> actual) {
        super(test);
        this.call = call;
        this.expected = expected;
        this.actual = actual;
    }

    @Override
    public boolean passed() {
        return expected != null && expected.isAssignableFrom(actual);
    }

    @Override
    public String errorCode() {
        return "Wrong Exception Type";
    }

    public Tester.MethodCall getMethodCall() {
        return call;
    }

    public Class<T> getExpected() {
        return expected;
    }

    public Class<R> getActual() {
        return actual;
    }

    @Override
    public String getMessage() {
        if (passed())
            return "Expected exception: " + expected.getSimpleName();
        return call + " threw the wrong type of exception: Expected " + expected.getSimpleName() + " but threw " + actual.getSimpleName();
    }
}
