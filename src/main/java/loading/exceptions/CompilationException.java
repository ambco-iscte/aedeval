package loading.exceptions;

import extensions.Extensions;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.Collections;
import java.util.List;

public class CompilationException extends Exception {

    private final List<Diagnostic<? extends JavaFileObject>> diagnostics;

    public CompilationException(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        super(Extensions.joinToString(System.lineSeparator(), diagnostics, CompilationException::message));
        this.diagnostics = diagnostics;
    }

    private static String message(Diagnostic<? extends JavaFileObject> diagnostic) {
        return "Error at line " + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(null);
    }

    public CompilationException(String message) {
        super(message);
        this.diagnostics = Collections.emptyList();
    }

    public List<Diagnostic<? extends JavaFileObject>> getDiagnostics() {
        return this.diagnostics;
    }
}
