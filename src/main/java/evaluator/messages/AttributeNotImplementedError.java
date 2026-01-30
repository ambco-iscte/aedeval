package evaluator.messages;

import evaluator.annotations.Test;

public class AttributeNotImplementedError extends Result {

    private final NoSuchFieldException cause;

    public AttributeNotImplementedError(Test test, NoSuchFieldException cause) {
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
        return "Attribute not implemented: " + cause.getMessage() + ". Did you use the correct name?";
    }
}
