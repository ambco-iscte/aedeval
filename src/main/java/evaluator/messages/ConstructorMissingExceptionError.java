package evaluator.messages;

import evaluator.Tester;
import evaluator.annotations.Test;

public class ConstructorMissingExceptionError<T extends Throwable> extends Result {

    private final Tester.ObjectInstantiation call;

    private final Class<T> expected;

    private final Throwable actual;

    public ConstructorMissingExceptionError(Test test, Tester.ObjectInstantiation call, Class<T> expected, Throwable actual) {
        super(test);
        this.call = call;
        this.expected = expected;
        this.actual = actual;
    }

    @Override
    public String errorCode() {
        return "Missing Required Exception";
    }

    public Tester.ObjectInstantiation getMethodCall() {
        return call;
    }

    public Class<T> getExpected() {
        return expected;
    }

    public Throwable getActual() {
        return actual;
    }

    @Override
    public boolean passed() {
        return false;
    }

    @Override
    public String getMessage() {
        if (actual == null)
            return call + " did not throw any exceptions, but should have thrown " + expected.getSimpleName();
        else
            return call + " threw the wrong exception type: Expected " + expected.getSimpleName() + " but threw " + actual.getClass().getSimpleName() + ".";
    }
}
