package evaluator.messages.methods;

import evaluator.Tester;
import evaluator.annotations.Test;
import evaluator.messages.Result;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class MethodInvocationSideEffect extends Result {

    private final Tester.MethodCall call;

    private final Tester.SideEffectChecker checker;

    private final boolean result;

    private final Throwable exception;

    public MethodInvocationSideEffect(Test test, Tester.MethodCall call, Tester.SideEffectChecker checker) {
        super(test);
        this.call = call;
        this.checker = checker;

        boolean checked;
        Throwable error;

        try {
            checked = checker.check();
            error = null;
        } catch (ExecutionException e) {
            checked = false;
            error = e.getCause();
        } catch (Exception e) {
            checked = false;
            error = e;
        }

        result = checked;
        exception = error;
    }

    public Tester.MethodCall getMethodCall() {
        return call;
    }

    public boolean producedSideEffect() {
        return result;
    }

    public Optional<Throwable> getException() {
        return Optional.ofNullable(exception);
    }

    @Override
    public String errorCode() {
        return "Incorrect Side Effect";
    }

    @Override
    public boolean passed() {
        return result && exception == null;
    }

    @Override
    public String getMessage() {
        String produced = passed() ? "produced" : "did not produce";

        String message = String.format(
            "Calling %s " + produced + " the intended effect: %s",
            call.toString(),
            checker.message(passed())
        );

        if (exception != null) {
            message += ", because something in your code caused an unexpected " + exception.getClass().getSimpleName() + ".";
            if (exception instanceof TimeoutException)
                message += " Have you checked for infinite loops or unbounded recursion?";
        } else
            message += ".";

        return message;
    }
}
