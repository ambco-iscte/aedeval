package evaluator.messages;

import evaluator.annotations.Test;
import extensions.Extensions;

import java.util.concurrent.TimeoutException;

public class ObjectInstantiationError extends Result {

    private final Class<?> type;

    private final Object[] arguments;

    private final Throwable exception;

    public ObjectInstantiationError(Test test, Class<?> type, Object[] arguments, Throwable exception) {
        super(test);
        this.type = type;
        this.arguments = arguments;
        this.exception = exception;
    }

    @Override
    public String errorCode() {
        return "Instantiation Error";
    }

    public Class<?> getType() {
        return type;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public Throwable getException() {
        return exception;
    }

    @Override
    public boolean passed() {
        return false;
    }

    @Override
    public String getMessage() {
        if (exception instanceof TimeoutException)
            return String.format(
                    "Constructor for new %s(%s) threw unexpected %s. Did you check for infinite loops?",
                    type.getSimpleName(),
                    Extensions.joinToString(arguments),
                    exception.getClass().getSimpleName()
            );
        return String.format(
                "Constructor for new %s(%s) threw unexpected %s: %s",
                type.getSimpleName(),
                Extensions.joinToString(arguments),
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
    }
}
