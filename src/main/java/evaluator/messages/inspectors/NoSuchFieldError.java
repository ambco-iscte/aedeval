package evaluator.messages.inspectors;

import evaluator.annotations.Test;
import evaluator.messages.Result;

public class NoSuchFieldError extends Result {

    private final NoSuchFieldException cause;

    public NoSuchFieldError(Test test, NoSuchFieldException cause) {
        super(test);
        this.cause = cause;
    }

    @Override
    public String errorCode() {
        return "Attribute Not Implemented";
    }

    public NoSuchFieldException getCause() {
        return cause;
    }

    @Override
    public boolean passed() {
        return false;
    }

    @Override
    public String getMessage() {
        return "Field not implemented: " + cause.getMessage() + ". Did you use the correct name and type?";
    }
}
