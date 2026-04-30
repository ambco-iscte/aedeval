package evaluator.messages.inspectors;

import evaluator.annotations.Test;
import evaluator.messages.Result;

public class ReferencedClassNotFoundError extends Result {

    private final NoClassDefFoundError cause;

    public ReferencedClassNotFoundError(Test currentTest, NoClassDefFoundError cause) {
        super(currentTest);
        this.cause = cause;
    }

    public NoClassDefFoundError getCause() {
        return cause;
    }

    @Override
    public String errorCode() {
        return "Undefined Class Reference";
    }

    @Override
    public boolean passed() {
        return false;
    }

    @Override
    public String getMessage() {
        return String.format("Undefined class referenced when executing test \"%s\": %s", getTest().description(), cause.getMessage());
    }
}
