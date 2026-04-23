package loading.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.HashSet;
import java.util.Optional;

public class ImportStatementCollector extends VoidVisitorAdapter<Void> {

    private static final String JAVA_LANG_PACKAGE = "java.lang";

    private final CompilationUnit unit;
    private final String[] allowedPackages;
    private final HashSet<ImportDeclaration> used = new HashSet<>();
    private final HashSet<ImportDeclaration> missing = new HashSet<>();

    public ImportStatementCollector(CompilationUnit unit, String[] allowedPackages) {
        this.unit = unit;
        this.allowedPackages = allowedPackages;
    }

    public HashSet<ImportDeclaration> getUsed() {
        return used;
    }

    public HashSet<ImportDeclaration> getUnused() {
        HashSet<ImportDeclaration> all = new HashSet<>(unit.getImports());
        all.removeAll(used);
        return all;
    }

    public HashSet<ImportDeclaration> getMissing() {
        return this.missing;
    }

    private static boolean isImport(ImportDeclaration imp, ResolvedTypeDeclaration type) {
        String name = imp.getNameAsString();
        return name.equals(type.getQualifiedName()) || (imp.isAsterisk() && name.startsWith(type.getPackageName()));
    }

    private static boolean isJavaClassQualifiedName(String qualifiedName) {
        try {
            Class.forName(qualifiedName);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private Optional<String> allowedClassQualifiedNameFromSimpleName(String simpleName) {
        for (String allowedPackage : this.allowedPackages) {
            String qualifiedName = allowedPackage + "." + simpleName;
            if (isJavaClassQualifiedName(qualifiedName))
                return Optional.of(qualifiedName);
        }
        return Optional.empty();
    }

    private void handleResolvedTypeDeclaration(ResolvedTypeDeclaration type) {
        if (type == null)
            return;

        boolean typeIsImported = false;
        for (ImportDeclaration imp : unit.getImports()) {
            if (isImport(imp, type)) {
                used.add(imp);
                typeIsImported = true;
                break;
            }
        }

        if (!typeIsImported && !type.getPackageName().equals(JAVA_LANG_PACKAGE) && isJavaClassQualifiedName(type.getQualifiedName()))
            missing.add(new ImportDeclaration(type.getQualifiedName(), false, false, false));
    }

    private void handleResolvedType(ResolvedType type) {
        if (type == null)
            return;
        if (type.isArray())
            handleResolvedType(type.asArrayType().getComponentType());
        if (type.isReferenceType())
            handleResolvedTypeDeclaration(type.asReferenceType().getTypeDeclaration().orElse(null));
    }

    private void handleType(Type type) {
        if (type == null)
            return;
        try {
            handleResolvedType(type.resolve());
        } catch (Exception ignored) {
            if (type.isClassOrInterfaceType()) {
                Optional<String> qualifiedName = allowedClassQualifiedNameFromSimpleName(type.asClassOrInterfaceType().getNameAsString());
                if (qualifiedName.isPresent() && isJavaClassQualifiedName(qualifiedName.get()))
                    missing.add(new ImportDeclaration(qualifiedName.get(), false, false, false));
            }
        }
    }

    private void handleFieldAccess(FieldAccessExpr field) {
        if (field == null)
            return;
        try {
            handleResolvedTypeDeclaration(field.resolve().asField().declaringType());
            handleExpression(field.getScope());
        } catch (Exception ignored) { }
    }

    private void handleNameExpression(NameExpr name) {
        if (name == null)
            return;
        try {
            handleResolvedType(name.calculateResolvedType());
        } catch (Exception ignored) { }

        for (ImportDeclaration imp : unit.getImports()) {
            if (imp.isStatic() && imp.getNameAsString().endsWith(name.getNameAsString())) {
                used.add(imp);
                break;
            }
        }
    }

    private void handleExpression(Expression expression) {
        if (expression == null)
            return;
        try {
            handleResolvedType(expression.calculateResolvedType());
        } catch (Exception ignored) { }
        if (expression.isNameExpr())
            handleNameExpression(expression.asNameExpr());
        if (expression.isFieldAccessExpr())
            handleFieldAccess(expression.asFieldAccessExpr());
    }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        handleNameExpression(new NameExpr(n.getName()));
        handleExpression(n.getScope().orElse(null));
        for (Expression argument : n.getArguments()) {
            handleExpression(argument);
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        handleType(n.getType());
        handleExpression(n.getScope().orElse(null));
        for (Expression argument : n.getArguments()) {
            handleExpression(argument);
        }
        super.visit(n, arg);
    }

    public void visit(MethodDeclaration n, Void arg) {
        handleType(n.getType());
        for (Parameter argument : n.getParameters()) {
            handleType(argument.getType());
        }
        super.visit(n, arg);
    }

    public void visit(ClassOrInterfaceDeclaration n, Void arg) {
        for (ClassOrInterfaceType type : n.getImplementedTypes())
            handleType(type);
        for (ClassOrInterfaceType type : n.getPermittedTypes())
            handleType(type);
        for (ClassOrInterfaceType type : n.getExtendedTypes())
            handleType(type);
        super.visit(n, arg);
    }
}
