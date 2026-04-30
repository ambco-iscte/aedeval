package evaluator.messages.methods;

import evaluator.annotations.Test;
import evaluator.messages.Result;
import extensions.Extensions;

import java.lang.reflect.Method;

public class IncorrectMethodNameError extends Result {

    private final Class<?> type;

    private final String expected;

    private final Method found;

    public IncorrectMethodNameError(Test currentTest, Class<?> type, String expected, Method found) {
        super(currentTest);
        this.type = type;
        this.expected = expected;
        this.found = found;
    }

    public String getExpectedName() {
        return expected;
    }

    public Method getClosestMatch() {
        return found;
    }

    @Override
    public String errorCode() {
        return "Wrong File Name";
    }

    @Override
    public boolean passed() {
        return false;
    }

    @Override
    public String getMessage() {
        String[] expectedParamTypeNames = new String[found.getParameterCount()];
        for (int i = 0; i < expectedParamTypeNames.length; i++)
            expectedParamTypeNames[i] = found.getParameterTypes()[i].getSimpleName();
        String expectedSignature = "%s %s.%s(%s)".formatted(
            found.getReturnType().getSimpleName(),
            type.getSimpleName(),
            expected,
            Extensions.joinToString(expectedParamTypeNames)
        );

        return String.format("Could not find method <%s>, but found method with similar name: <%s>. " +
                "Make sure you implement methods with the intended name!", expectedSignature, Extensions.signature(found));
    }
}
