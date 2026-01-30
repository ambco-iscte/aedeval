package evaluator.messages;

import evaluator.annotations.Test;
import loading.CompilationException;
import loading.JavacErrorType;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CompilationError extends Result {

    private final File source;
    private final CompilationException cause;

    private static final String GENERIC_ARRAY_CREATION_MESSAGE = "Remember that, in Java, generic arrays need to be " +
            "created as Object[] and then \"ugly cast\" to the generic array you want.";

    public CompilationError(Test test, File source, CompilationException cause) {
        super(test);
        this.source = source;
        this.cause = cause;
    }

    @Override
    public String errorCode() {
        return "Compilation Error";
    }

    @Override
    public boolean passed() {
        return false;
    }

    public CompilationException getCause() {
        return cause;
    }

    public List<JavacErrorType> getCompilationErrorTypes() {
        List<JavacErrorType> types = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : cause.getDiagnostics()) {
            JavacErrorType type = JavacErrorType.of(diagnostic);
            if (type != null)
                types.add(type);
        }
        return types;
    }

    private boolean isGenericArrayCreation() {
        if (cause.getMessage().endsWith("generic array creation"))
            return true;
        for (JavacErrorType type : getCompilationErrorTypes()) {
            if (type == JavacErrorType.GenericArrayCreation)
                return true;
        }
        return false;
    }

    @Override
    public String getMessage() {
        String message = String.format("Could not compile file %s: %s", source.getName(), cause.getMessage().replace(source.getAbsolutePath(), source.getName()));
        if (isGenericArrayCreation())
            message += String.format(". %s", GENERIC_ARRAY_CREATION_MESSAGE);
        return message;
    }
}