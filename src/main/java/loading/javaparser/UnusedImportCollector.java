package loading.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.HashSet;

public class UnusedImportCollector extends VoidVisitorAdapter<Void> {

    private final CompilationUnit unit;
    private final HashSet<ImportDeclaration> used = new HashSet<>();

    public UnusedImportCollector(CompilationUnit unit) {
        this.unit = unit;
    }

    public HashSet<ImportDeclaration> getUsed() {
        return used;
    }

    public HashSet<ImportDeclaration> getUnused() {
        HashSet<ImportDeclaration> all = new HashSet<>(unit.getImports());
        all.removeAll(used);
        return all;
    }

    private void checkIsImported(ResolvedTypeDeclaration type) {
        if (type == null)
            return;
        for (ImportDeclaration imp : unit.getImports()) {
            if (imp.getNameAsString().equals(type.getQualifiedName()) || imp.isAsterisk() && imp.getNameAsString().startsWith(type.getPackageName()))
                used.add(imp);
        }
    }

    private void checkIsImported(ResolvedType type) {
        if (type == null)
            return;
        if (type.isArray())
            checkIsImported(type.asArrayType().getComponentType());
        if (type.isReferenceType())
            checkIsImported(type.asReferenceType().getTypeDeclaration().orElse(null));
    }

    private void checkIsImported(Type type) {
        if (type == null)
            return;
        try {
            checkIsImported(type.resolve());
        } catch (Exception ignored) { }
    }

    private void checkIsImportedField(FieldAccessExpr field) {
        if (field == null)
            return;
        try {
            checkIsImported(field.resolve().asField().declaringType());
            checkIsImportedExpression(field.getScope());
        } catch (Exception ignored) { }
    }

    private void checkIsImportedName(NameExpr name) {
        if (name == null)
            return;
        try {
            checkIsImported(name.calculateResolvedType());
        } catch (Exception ignored) { }
        for (ImportDeclaration imp : unit.getImports()) {
            if (imp.isStatic() && imp.getNameAsString().endsWith(name.getNameAsString()))
                used.add(imp);
        }
    }

    private void checkIsImportedExpression(Expression expression) {
        if (expression == null)
            return;
        try {
            checkIsImported(expression.calculateResolvedType());
        } catch (Exception ignored) { }
        if (expression.isNameExpr())
            checkIsImportedName(expression.asNameExpr());
        if (expression.isFieldAccessExpr())
            checkIsImportedField(expression.asFieldAccessExpr());
    }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        checkIsImportedName(new NameExpr(n.getName()));
        checkIsImportedExpression(n.getScope().orElse(null));
        for (Expression argument : n.getArguments()) {
            checkIsImportedExpression(argument);
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        checkIsImported(n.getType());
        checkIsImportedExpression(n.getScope().orElse(null));
        for (Expression argument : n.getArguments()) {
            checkIsImportedExpression(argument);
        }
        super.visit(n, arg);
    }
}
