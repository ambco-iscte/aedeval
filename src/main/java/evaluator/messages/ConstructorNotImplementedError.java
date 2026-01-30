package evaluator.messages;

import evaluator.annotations.Test;
import extensions.Extensions;

public class ConstructorNotImplementedError extends Result {

    private final Class<?> type;

    private final Class<?>[] parameterTypes;

    public ConstructorNotImplementedError(Test currentTest, Class<?> type, Class<?>[] parameterTypes) {
        super(currentTest);
        this.type = type;
        this.parameterTypes = parameterTypes;
    }

    public Class<?> getType() {
        return type;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    @Override
    public String errorCode() {
        return "Constructor Not Implemented";
    }

    @Override
    public boolean passed() {
        return false;
    }

    @Override
    public String getMessage() {
        String message;
        if (parameterTypes == null)
            message = String.format("Constructor not implemented: public %s()", type.getSimpleName());
        else
            message = String.format("Constructor not implemented: public %s(%s)", type.getSimpleName(), Extensions.joinToString(parameterTypes));
        return message + ". If you implemented the constructor, you might've used the wrong parameter types.";
    }
}
