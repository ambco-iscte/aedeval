package evaluator.messages;

import evaluator.annotations.Test;

import java.io.File;

public class MissingFileError extends Result {

    private final File directory;

    private final String name;

    public MissingFileError(Test test, File directory, String name) {
        super(test);
        this.directory = directory;
        this.name = name;
    }

    @Override
    public String errorCode() {
        return "Missing File";
    }

    public File getDirectory() {
        return directory;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean passed() {
        return false;
    }

    @Override
    public String getMessage() {
        return String.format("File not found: %s. Make sure you submit the .java file with the correct name!", name);
    }
}
