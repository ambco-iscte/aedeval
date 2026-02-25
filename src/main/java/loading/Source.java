package loading;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import extensions.Console;
import loading.exceptions.UnsupportedJavaFeatureException;
import loading.javaparser.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Arrays;

public final class Source {

    private static final ParserConfiguration.LanguageLevel JAVA_VERSION = ParserConfiguration.LanguageLevel.JAVA_25;

    private static void INIT() {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(JAVA_VERSION);
        JavaSymbolSolver solver = new JavaSymbolSolver(new CombinedTypeSolver(new ReflectionTypeSolver()));
        StaticJavaParser.getParserConfiguration().setSymbolResolver(solver);
    }

    static {
        INIT();
    }

    public static Visitable removed(Node node) {
        node.remove();
        return null;
    }

    /**
     * Is <code>n</code> the declaration of a compact main method?
     * @param n JavaParser method declaration.
     */
    public static boolean isMainMethod(MethodDeclaration n) {
        return n.getTypeAsString().equals("void") &&
               n.getNameAsString().equals("main") &&
               (n.getParameters().isEmpty() || (n.getParameters().size() == 1 && n.getParameters().get(0).getTypeAsString().equals("String[]")));
    }

    /**
     * Does the type have a main method?
     * @param type JavaParser type declaration.
     * @return True if the type declares a main method. False, otherwise.
     * @see #isMainMethod(MethodDeclaration)
     */
    private static boolean hasMainMethod(TypeDeclaration<?> type) {
        for (MethodDeclaration method : type.getMethods()) {
            if (isMainMethod(method))
                return true;
        }
        return false;
    }

    private static String getPrimaryTypeNameOrNull(CompilationUnit unit) {
        for (TypeDeclaration<?> type : unit.getTypes()) {
            if (type.isPublic() && hasMainMethod(type))
                return type.getNameAsString();
        }
        return null;
    }

    public static CompilationUnit clean(CompilationUnit unit, String[] allowedPackages) {
        // Configure Java Symbol Solver
        if (StaticJavaParser.getParserConfiguration().getSymbolResolver().isPresent()) {
            SymbolResolver resolver = StaticJavaParser.getParserConfiguration().getSymbolResolver().get();
            if (resolver instanceof JavaSymbolSolver javaSymbolSolver)
                javaSymbolSolver.inject(unit);
        }

        unit.setLineComment(" [" + LocalDateTime.now() + "] Source code cleaned by AED Evaluator.");

        // Remove package declaration
        PackageDeclaration pkg = unit.getPackageDeclaration().orElse(null);
        unit = unit.removePackageDeclaration();

        // Remove main method
        unit = (CompilationUnit) new MainMethodRemover().visit(unit, null);

        // Encapsulate control structure bodies
        // new ControlStructureBracketer().visit(unit, null);

        // Remove System and IO calls
        unit = (CompilationUnit) new SystemCallRemover().visit(unit, null);

        // Prevent static classes (some students accidentally did this)
        unit = (CompilationUnit) new ClassModifierRemover(Modifier.Keyword.STATIC).visit(unit, null);

        // Remove usages of illegal packages
        if (pkg == null) {
            unit = (CompilationUnit) new IllegalExpressionRemover(allowedPackages).visit(unit, null);
        } else if (allowedPackages == null) {
            String[] allowed = { pkg.getNameAsString() };
            unit = (CompilationUnit) new IllegalExpressionRemover(allowed).visit(unit, null);
        } else {
            String[] allowed = Arrays.copyOf(allowedPackages, allowedPackages.length + 1);
            allowed[allowed.length - 1] = pkg.getNameAsString();
            unit = (CompilationUnit) new IllegalExpressionRemover(allowed).visit(unit, null);
        }

        return unit;
    }

    /**
     * Processes a Java source code file by:
     * <ul>
     *     <li>Removing the main method, if present;</li>
     *     <li>Adding brackets to all control structures, if the body is a single expression;</li>
     *     <li>Removing any calls to methods in the System or IO classes.</li>
     *     <li>Removing any usages of not-allowed packages.</li>
     * </ul>
     * @param source Java source code file.
     * @param allowedPackages Names of allowed packages.
     * @throws FileNotFoundException If the file does not exist.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static CompilationUnit clean(File source, String[] allowedPackages) throws FileNotFoundException, ParseProblemException, UnsupportedJavaFeatureException {
        INIT();
        CompilationUnit unit = clean(StaticJavaParser.parse(source), allowedPackages);
        try {
            Files.writeString(source.toPath(), unit.toString());
        } catch (IOException e) {
            Console.error("Could not write cleaned code to file " + source.getPath() + ": " + e.getMessage());
        }
        return unit;
    }
}
