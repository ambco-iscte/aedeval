package loading;

import extensions.Extensions;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.Collections;
import java.util.List;

public class CompilationException extends Exception {

    private final List<Diagnostic<? extends JavaFileObject>> diagnostics;

    public CompilationException(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        super(Extensions.joinToString("; ", diagnostics,
                d -> "Error at line " + d.getLineNumber() + ": " + d.getMessage(null)
        ));
        this.diagnostics = diagnostics;
    }

    public CompilationException(String message) {
        super(message);
        this.diagnostics = Collections.emptyList();
    }

    public List<Diagnostic<? extends JavaFileObject>> getDiagnostics() {
        return this.diagnostics;
    }
}
