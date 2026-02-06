package loading;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import extensions.Console;
import loading.javaparser.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;

public class Source {

    private static final ParserConfiguration.LanguageLevel JAVA_VERSION = ParserConfiguration.LanguageLevel.JAVA_25;

    static {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(JAVA_VERSION);
        if (StaticJavaParser.getParserConfiguration().getSymbolResolver().isEmpty()) {
            CombinedTypeSolver solver = new CombinedTypeSolver();
            solver.add(new ReflectionTypeSolver());
            StaticJavaParser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(solver));
        }
    }

    /**
     * Is <code>n</code> the declaration of Java's main method?
     * @param n JavaParser method declaration.
     * @return True if the declaration matches <code>public static void main()</code> or
     * <code>public static void main(String[])</code>. False, otherwise.
     */
    public static boolean isMainMethod(MethodDeclaration n) {
        return n.isPublic() &&
               n.isStatic() &&
               n.getTypeAsString().equals("void") &&
               n.getNameAsString().equals("main") &&
               (n.getParameters().isEmpty() || (n.getParameters().size() == 1 && n.getParameters().get(0).getTypeAsString().equals("String[]")));
    }

    /**
     * Is <code>n</code> the declaration of an instance main method?
     * @param n JavaParser method declaration.
     * @return True if the declaration matches <code>void main()</code>. False, otherwise.
     */
    public static boolean isInstanceMainMethod(MethodDeclaration n) {
        return !n.isStatic() &&
                n.getTypeAsString().equals("void") &&
                n.getNameAsString().equals("main") &&
                n.getParameters().isEmpty();
    }

    /**
     * Does the type have a main method?
     * @param type JavaParser type declaration.
     * @return True if the type declares a main method. False, otherwise.
     * @see #isMainMethod(MethodDeclaration)
     */
    private static boolean hasMainMethod(TypeDeclaration<?> type) {
        for (MethodDeclaration method : type.getMethods()) {
            if (isMainMethod(method) || isInstanceMainMethod(method))
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

    /**
     * Processes a Java source code file by:
     * <p>
     * 1. Removing the main method, if present;
     * <p>
     * 2. Adding brackets to all control structures, if the body is a single expression;
     * <p>
     * 3. Removing any calls to methods in the System package.
     * @param source Java source code file.
     * @throws FileNotFoundException If the file does not exist.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static CompilationUnit clean(File source) throws UnsupportedJavaFeatureException, FileNotFoundException {
        CompilationUnit unit = StaticJavaParser.parse(source);

        // Java 25 Compact Files unsupported for now
        for (TypeDeclaration<?> type : unit.getTypes()) {
            if (type instanceof ClassOrInterfaceDeclaration klass && klass.isCompact())
                throw new UnsupportedJavaFeatureException(unit, klass, "compact files", JAVA_VERSION);
        }

        for (Comment comment : unit.getAllComments())
            comment.remove();
        unit.setLineComment(" [" + LocalDateTime.now() + "] Source code cleaned by AED Evaluator.");

        // Remove package declaration
        unit.removePackageDeclaration();

        // Remove main method
        new MainMethodRemover().visit(unit, null);

        // Remove unused imports
        /*
        UnusedImportFinder unused = new UnusedImportFinder();
        unused.visit(unit, null);
        for (ImportDeclaration it : unused.getUnusedImports()) {
            System.out.println("Unused: " + it);
        }
        new ImportStatementRemover(unused.getUnusedImports()).visit(unit, null);
         */

        // Encapsulate control structure bodies
        new ControlStructureBracketer().visit(unit, null);

        // Remove System.exit calls
        new SystemCallRemover().visit(unit, null);

        try {
            Files.writeString(source.toPath(), unit.toString());
        } catch (IOException e) {
            Console.error("Could not write cleaned code to file " + source.getPath() + ": " + e.getMessage());
        }

        return unit;
    }

    public static void main(String[] args) {
        CompilationUnit unit = StaticJavaParser.parse("""
                void main() {
                    IO.println("Hello world!");
                }
        """);
        System.out.println(unit.toString());
    }
}
