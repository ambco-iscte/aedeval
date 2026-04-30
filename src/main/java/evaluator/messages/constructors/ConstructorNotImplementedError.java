package evaluator.messages.constructors;

import evaluator.annotations.Test;
import evaluator.messages.Result;
import extensions.Extensions;

public class ConstructorNotImplementedError extends Result {

    private final Class<?> type;

    private final Class<?>[] parameterTypes;

    private final String[] parameterTypeNames;

    public ConstructorNotImplementedError(Test currentTest, Class<?> type, Class<?>[] parameterTypes) {
        super(currentTest);
        this.type = type;
        this.parameterTypes = parameterTypes;

        parameterTypeNames = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypeNames.length; i++)
            parameterTypeNames[i] = parameterTypes[i].getSimpleName();
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
            message = String.format("Constructor not implemented: public %s(%s)", type.getSimpleName(), Extensions.joinToString(parameterTypeNames));
        return message + ". If you implemented the constructor, you might've used the wrong parameter types.";
    }
}
