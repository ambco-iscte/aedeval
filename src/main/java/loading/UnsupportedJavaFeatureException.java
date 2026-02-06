package loading;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

public class UnsupportedJavaFeatureException extends CompilationException {

    private final CompilationUnit unit;
    private final Node unsupported;
    private ParserConfiguration.LanguageLevel javaLanguageLevel;

    public UnsupportedJavaFeatureException(CompilationUnit unit, Node unsupported, String feature, ParserConfiguration.LanguageLevel javaLanguageLevel) {
        super("Unsupported " + getFriendlyJavaVersionName(javaLanguageLevel) + " language feature: " + feature);
        this.unit = unit;
        this.unsupported = unsupported;
    }

    public CompilationUnit getCompilationUnit() {
        return unit;
    }

    private Node getUnsupportedElement() {
        return unsupported;
    }

    public ParserConfiguration.LanguageLevel getJavaLanguageLevel() {
        return javaLanguageLevel;
    }

    private static String getFriendlyJavaVersionName(ParserConfiguration.LanguageLevel level) {
        return level.toString().replaceFirst("_", " ").replaceAll("_", ".");
    }
}
