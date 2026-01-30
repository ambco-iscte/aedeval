package evaluator.messages;

import evaluator.annotations.Test;

public class IncorrectFileNameError extends Result {

    private final String expected;

    private final String found;

    public IncorrectFileNameError(Test currentTest, String expected, String found) {
        super(currentTest);
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
        return String.format("Could not find file %s, but found file with similar name: %s. " +
                "Make sure you submit the .java file with the correct name!", expected, found);
    }
}
