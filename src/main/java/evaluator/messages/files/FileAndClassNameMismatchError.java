package evaluator.messages.files;

import evaluator.annotations.Test;

public class FileAndClassNameMismatchError extends IncorrectFileNameError {

    public enum Mismatch {
        WrongFileNameRightClassName,
        RightFileNameWrongClassName
    }

    private final String expectedFileName;
    private final String foundFileName;
    private final String foundClassName;
    private final Mismatch mismatch;

    public FileAndClassNameMismatchError(Test currentTest, String expected, String found, String clazz, Mismatch mismatch) {
        super(currentTest, expected, found);
        this.expectedFileName = expected;
        this.foundFileName = found;
        this.foundClassName = clazz;
        this.mismatch = mismatch;
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
        return switch (mismatch) {
            case WrongFileNameRightClassName ->
                String.format("Could not find file %s, but found class %s in a file named %s. " +
                    "Make sure your class and its file have exactly the same name!", expectedFileName, foundClassName, foundFileName);

            case RightFileNameWrongClassName -> String.format("The file %s is present, but the class %s does not match " +
                    "the file name. Make sure your class and its file have exactly the same name!", expectedFileName, foundClassName);
        };
    }
}
