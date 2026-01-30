package evaluator.messages;

import evaluator.annotations.Test;

public class IncorrectMethodNameError extends Result {

    private final Class<?> type;

    private final String expected;

    private final String found;

    public IncorrectMethodNameError(Test currentTest, Class<?> type, String expected, String found) {
        super(currentTest);
        this.type = type;
        this.expected = expected;
        this.found = found;
    }

    public String getExpectedName() {
        return expected;
    }

    public String getClosestMatchName() {
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
        return String.format("Could not find method %s.%s, but found method with similar name: %s. " +
                "Make sure you implement methods with the intended name!", type.getSimpleName(), expected, found);
    }
}
