package evaluator.messages;

import evaluator.annotations.Test;

public class FileAndClassNameMismatchError extends IncorrectFileNameError {

    private final String expectedFileName;
    private final String foundFileName;
    private final String foundClassName;

    public FileAndClassNameMismatchError(Test currentTest, String expected, String found, String clazz) {
        super(currentTest, expected, found);
        this.expectedFileName = expected;
        this.foundFileName = found;
        this.foundClassName = clazz;
    }

    public String getExpectedFileName() {
        return expectedFileName;
    }

    public String getFoundFileName() {
        return foundFileName;
    }

    public String getFoundClassName() {
        return foundClassName;
    }

    @Override
    public String getMessage() {
        return String.format("Could not find file %s, but found class %s in a file named %s. " +
                "Make sure your class and its file have exactly the same name!", expectedFileName, foundClassName, foundFileName);
    }
}
