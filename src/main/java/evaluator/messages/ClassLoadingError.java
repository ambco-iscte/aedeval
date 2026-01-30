package evaluator.messages;

import evaluator.annotations.Test;

import java.io.File;

public class ClassLoadingError extends Result {

    private final File source;
    private final Throwable cause;

    public ClassLoadingError(Test test, File source, Throwable cause) {
        super(test);
        this.source = source;
        this.cause = cause;
    }

    @Override
    public String errorCode() {
        return "Loading Error";
    }

    @Override
    public boolean passed() {
        return false;
    }

    @Override
    public String getMessage() {
        return String.format("Could not load class from file %s: %s", source.getName(), cause.getMessage());
    }
}