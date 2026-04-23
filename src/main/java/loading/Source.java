package loading;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import extensions.out.Console;
import loading.exceptions.PackageNotAllowedException;
import loading.javaparser.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static extensions.Extensions.tryOrElse;

public final class Source {

    private static final ParserConfiguration.LanguageLevel JAVA_VERSION = ParserConfiguration.LanguageLevel.JAVA_25;

    private static void INIT() {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(JAVA_VERSION);
        JavaSymbolSolver solver = new JavaSymbolSolver(new ReflectionTypeSolver());
        StaticJavaParser.getParserConfiguration().setSymbolResolver(solver);
    }

    static {
        INIT();
    }

    public static Visitable removed(Node node) {
        node.remove();
        return null;
    }

    public static boolean isCallArgument(Expression n) {
        if (n == null)
            return false;
        MethodCallExpr call = n.findAncestor(MethodCallExpr.class).orElse(null);
        if (call != null)
            return call.getArguments().contains(n);
        ObjectCreationExpr creation = n.findAncestor(ObjectCreationExpr.class).orElse(null);
        if (creation != null)
            return creation.getArguments().contains(n);
        return false;
    }

    /**
     * Is <code>n</code> the declaration of a compact main method?
     * @param n JavaParser method declaration.
     */
    public static boolean isMainMethod(MethodDeclaration n) {
        return n.getTypeAsString().equals(void.class.getSimpleName().toLowerCase()) &&
               n.getNameAsString().equals("main") &&
               (n.getParameters().isEmpty() || (n.getParameters().size() == 1 && n.getParameters().get(0).getTypeAsString().equals(String.class.arrayType().getSimpleName())));
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

    public static CompilationUnit clean(final CompilationUnit unit, final String[] allowedPackages) throws PackageNotAllowedException {
        if (unit == null)
            return null;
        CompilationUnit cu = unit.clone();

        // Configure Java Symbol Solver
        if (StaticJavaParser.getParserConfiguration().getSymbolResolver().isPresent()) {
            SymbolResolver resolver = StaticJavaParser.getParserConfiguration().getSymbolResolver().get();
            if (resolver instanceof JavaSymbolSolver javaSymbolSolver)
                javaSymbolSolver.inject(cu);
        }

        for (Comment comment : cu.findAll(Comment.class))
            comment.remove();
        cu = (CompilationUnit) cu.setLineComment(" [" + LocalDateTime.now() + "] Source code cleaned by AED Evaluator.");

        // Remove package declaration
        PackageDeclaration pkg = cu.getPackageDeclaration().orElse(null);
        cu = cu.removePackageDeclaration();

        // Remove main method
        cu = (CompilationUnit) new MainMethodRemover().visit(cu, null);

        // Remove System and IO calls
        Method[] allowedSystemCalls = new Method[0];
        try {
            allowedSystemCalls = new Method[] {
                java.lang.System.class.getDeclaredMethod("currentTimeMillis"),
                java.lang.System.class.getDeclaredMethod("nanoTime"),
            };
        } catch (NoSuchMethodException ignored) { }
        cu = (CompilationUnit) new SystemCallRemover(allowedSystemCalls).visit(cu, null);

        // Match constructor definitions to declaring class
        cu = (CompilationUnit) new ConstructorValidator().visit(cu, null);

        // Remove usages of illegal packages
        TypeDeclaration<?> self = cu.getTypes().getFirst().orElse(null);
        IllegalExpressionRemover remover;
        if (pkg == null) {
            remover = new IllegalExpressionRemover(self, allowedPackages);
        } else if (allowedPackages == null) {
            String[] allowed = { pkg.getNameAsString() };
            remover = new IllegalExpressionRemover(self, allowed);
        } else {
            String[] allowed = Arrays.copyOf(allowedPackages, allowedPackages.length + 1);
            allowed[allowed.length - 1] = pkg.getNameAsString();
            remover = new IllegalExpressionRemover(self, allowed);
        }
        cu = (CompilationUnit) remover.visit(cu, null);

        // Did the student use illegal packages? Throw an exception.
        Map<String, Node[]> illegalPackageUsages = remover.getProhibitedUsages();
        if (!illegalPackageUsages.isEmpty())
            throw new PackageNotAllowedException(illegalPackageUsages, allowedPackages);

        // Remove unused imports (hopefully doesn't break anything)
        ImportStatementCollector visitor = new ImportStatementCollector(cu, allowedPackages);
        visitor.visit(cu, null);
        for (ImportDeclaration imp : visitor.getUnused())
            cu.remove(imp);
        for (ImportDeclaration imp : visitor.getMissing())
            cu = cu.addImport(imp);

        return cu;
    }

    /**
     * Processes a Java source code file by:
     * <ul>
     *     <li>Removing the main method, if present;</li>
     *     <li>Removing any calls to methods in the System or IO classes.</li>
     *     <li>Removing any usages of not-allowed packages.</li>
     *     <li>Removing unused imports and adding missing imports.</li>
     * </ul>
     * @param source Java source code file.
     * @param allowedPackages Names of allowed packages.
     * @throws FileNotFoundException If the file does not exist.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static CompilationUnit clean(File source, String[] allowedPackages) throws FileNotFoundException, ParseProblemException, PackageNotAllowedException {
        INIT();
        CompilationUnit unit = clean(StaticJavaParser.parse(source), allowedPackages);
        try {
            Files.writeString(source.toPath(), unit.toString());
        } catch (IOException e) {
            Console.error("Could not write cleaned code to file " + source.getPath() + ": " + e.getMessage());
        }
        return unit;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static Path renameType(final CompilationUnit unit, File file, String type, String name) throws ParseProblemException, IOException {
        CompilationUnit cu = unit == null ? StaticJavaParser.parse(file) : unit;
        if (cu == null)
            return null;

        final TypeDeclaration<?> target = findTypeWithName(cu, type);
        if (target == null)
            return null;

        if (target.getNameAsString().equals(name))
            return file.toPath();

        cu = (CompilationUnit) new TypeRenamerVisitor(target).visit(cu, name);
        return java.nio.file.Files.writeString(file.toPath(), cu.toString());
    }

    @SuppressWarnings("UnusedReturnValue")
    public static Path renameType(File file, String type, String name) throws ParseProblemException, IOException {
        return renameType(tryOrElse(() -> StaticJavaParser.parse(file), null), file, type, name);
    }

    public static String findFirstPublicTypeName(File file) {
        CompilationUnit unit = tryOrElse(() -> StaticJavaParser.parse(file), null);
        if (unit == null)
            return null;
        return findFirstPublicTypeName(unit);
    }

    @SuppressWarnings("rawtypes")
    public static String getCompactClassInnerNameOrDefault(TypeDeclaration<?> type) {
        if (type.isClassOrInterfaceDeclaration() && type.asClassOrInterfaceDeclaration().isCompact()) {
            Optional<TypeDeclaration> inner = type.asClassOrInterfaceDeclaration().findFirst(
                TypeDeclaration.class,
                (node) -> !node.equals(type)
            );
            if (inner.isPresent())
                return getCompactClassInnerNameOrDefault(inner.get());
        }
        return type.getNameAsString();
    }

    public static TypeDeclaration<?> findFirstPublicType(CompilationUnit unit) {
        NodeList<TypeDeclaration<?>> types = unit.getTypes();
        if (types.isEmpty())
            return null;

        if (types.size() == 1 && types.getFirst().isPresent())
            return types.getFirst().get();

        for (TypeDeclaration<?> type : types) {
            if (type.isPublic()) // && hasMainMethod(type))
                return type;
        }

        return null;
    }

    public static String findFirstPublicTypeName(CompilationUnit unit) {
        TypeDeclaration<?> first = findFirstPublicType(unit);
        if (first == null)
            return null;
        return getCompactClassInnerNameOrDefault(first);
    }

    private static TypeDeclaration<?> findTypeWithName(final CompilationUnit unit, String name) {
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            if (type.getNameAsString().equals(name))
                return type;
        }
        return null;
    }

    private static class TypeRenamerVisitor extends ModifierVisitor<String> {
        private final String target;

        public TypeRenamerVisitor(TypeDeclaration<?> target) {
            this.target = target.getNameAsString();
        }

        @Override
        public Visitable visit(ClassOrInterfaceType n, String data) {
            if (tryOrElse(() -> n.resolve().describe().equals(target), n.getNameAsString().equals(target)))
                return super.visit(n.setName(data), data);
            return super.visit(n, data);
        }

        @Override
        public Visitable visit(ClassOrInterfaceDeclaration n, String data) {
            if (Objects.equals(n.getNameAsString(), target))
                return super.visit(n.setName(data), data);
            return super.visit(n, data);
        }

        @Override
        public Visitable visit(ConstructorDeclaration n, String data) {
            if (Objects.equals(n.getNameAsString(), target))
                return super.visit(n.setName(data), data);
            return super.visit(n, data);
        }
    }
}
