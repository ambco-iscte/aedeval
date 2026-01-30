package evaluator.messages;

import evaluator.Tester;
import evaluator.annotations.Test;

public class MethodInvocationSideEffect extends Result {

    private final Tester.MethodCall call;

    private final Tester.SideEffectChecker checker;

    private final boolean result;

    public MethodInvocationSideEffect(Test test, Tester.MethodCall call, Tester.SideEffectChecker checker, boolean result) {
        super(test);
        this.call = call;
        this.checker = checker;
        this.result = result;
    }

    public Tester.MethodCall getMethodCall() {
        return call;
    }

    public boolean getSideEffectValue() {
        return result;
    }

    @Override
    public String errorCode() {
        return "Incorrect Side Effect";
    }

    @Override
    public boolean passed() {
        return result;
    }

    @Override
    public String getMessage() {
        String produced = passed() ? "produced" : "did not produce";
        return String.format(
                "Calling %s " + produced + " the intended effect: %s",
                call.toString(),
                checker.message(passed())
        );
    }
}
