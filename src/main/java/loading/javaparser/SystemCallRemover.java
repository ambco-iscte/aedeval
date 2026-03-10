package loading.javaparser;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import loading.Source;

public class SystemCallRemover extends ModifierVisitor<Void> {

    private static final Class<System> SYSTEM = java.lang.System.class;
    private static final Class<IO> IO = java.lang.IO.class;

    private static boolean isSystemOrIOType(ResolvedTypeDeclaration type) {
        return type.getQualifiedName().equals(SYSTEM.getCanonicalName()) || type.getQualifiedName().equals(IO.getCanonicalName());
    }

    private static boolean isSystemOrIOField(FieldAccessExpr n) {
        try {
            return isSystemOrIOType(n.resolve().asField().declaringType());
        } catch (Exception ignored) { }

        Expression owner = n.getScope();
        if (owner.isNameExpr()) {
            String scope = owner.asNameExpr().getName().getIdentifier();
            return scope.equals(SYSTEM.getSimpleName()) || scope.equals(IO.getSimpleName());
        }
        return false;
    }

    private static boolean isSystemOrIOCall(MethodCallExpr expression) {
        if (expression == null)
            return false;

        if (expression.getScope().isEmpty())
            return false;

        Expression scope = expression.getScope().get();
        if (scope.isFieldAccessExpr())
            return isSystemOrIOField(scope.asFieldAccessExpr());

        if (scope.isNameExpr()) {
            String identifier = scope.asNameExpr().getName().getIdentifier();
            return identifier.equals(SYSTEM.getSimpleName()) || identifier.equals(IO.getSimpleName());
        }

        try {
            return isSystemOrIOType(expression.resolve().declaringType());
        } catch (Exception ignored) { }

        return false;
    }

    @Override
    public Visitable visit(MethodCallExpr n, Void arg) {
        if (isSystemOrIOCall(n) && !Source.isCallArgument(n))
            return Source.removed(n);
        return super.visit(n, arg);
    }

    /*
    @Override
    public Visitable visit(FieldAccessExpr n, Void arg) {
        if (isSystemOrIOField(n) && !Source.isCallArgument(n))
            return Source.removed(n);
        return super.visit(n, arg);
    }
     */
}
