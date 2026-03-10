package loading.exceptions;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;

import java.io.File;

public class CannotParseCompactClassException extends RuntimeException {

    private static final String MESSAGE = "Cannot compare the positions of nodes if container node does not have a range.";

    private final File file;

    public CannotParseCompactClassException(File file) {
        super("JavaParser cannot parse compact class with comments: " + file.getPath());
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public static boolean affects(File source) {
        try {
            StaticJavaParser.parse(source);
        } catch (ParseProblemException e) {
            if (e.getMessage() != null && e.getMessage().contains(MESSAGE))
                return true;
        } catch (Exception ignored) { }
        return false;
    }
}
