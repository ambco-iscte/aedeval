package loading;

import java.io.File;

public class ClassLoadingException extends Exception {


    private final File source;
    private final Throwable cause;

    public ClassLoadingException(File source, Throwable cause) {
        super(cause);
        this.source = source;
        this.cause = cause;
    }

    public File getSourceFile() {
        return source;
    }

    public Throwable cause() {
        return cause;
    }
}
