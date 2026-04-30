package evaluator.messages.methods;

import evaluator.annotations.Test;
import evaluator.messages.Result;

public class MethodNotImplementedError extends Result {

    private final NoSuchMethodException cause;

    public MethodNotImplementedError(Test test, NoSuchMethodException cause) {
        super(test);
        this.cause = cause;
    }

    @Override
    public String errorCode() {
        return "Method Not Implemented";
    }

    public NoSuchMethodException getCause() {
        return cause;
    }

    @Override
    public boolean passed() {
        return false;
    }

    @Override
    public String getMessage() {
        return "Method not implemented: <" + cause.getMessage() + ">. Are you sure you used the correct name, return type, and parameter types?";
    }
}
