package loading.javaparser;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionMethodDeclaration;
import extensions.Extensions;
import loading.Source;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

import static extensions.Extensions.tryOrElse;

public class SystemCallRemover extends ModifierVisitor<Void> {

    private static final Class<System> SYSTEM = java.lang.System.class;
    private static final Class<IO> IO = java.lang.IO.class;

    private final Method[] allowed;

    public SystemCallRemover() {
        this.allowed = new Method[0];
    }

    public SystemCallRemover(Method[] allowed) {
        this.allowed = Objects.requireNonNullElseGet(allowed, () -> new Method[0]);
        for (Method method : this.allowed) {
            Class<?> declaring = method.getDeclaringClass();
            if (!declaring.equals(SYSTEM) && !declaring.equals(IO))
                throw new IllegalArgumentException("Not a System or IO call: " + Extensions.signature(method));
        }
    }

    private static boolean isSystemOrIOType(ResolvedTypeDeclaration type) {
        return type.getQualifiedName().equals(SYSTEM.getCanonicalName()) || type.getQualifiedName().equals(IO.getCanonicalName());
    }

    private static boolean isResolvedDeclaration(ReflectionMethodDeclaration reflection, Method method) {
        Field m = tryOrElse(() -> reflection.getClass().getDeclaredField("method"), null);
        return m != null && m.trySetAccessible() && tryOrElse(() -> Objects.equals(m.get(reflection), method), false);
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

    private boolean isSpecificAllowed(MethodCallExpr expr) {
        ResolvedMethodDeclaration declaration = tryOrElse(expr::resolve, null);
        if (declaration instanceof ReflectionMethodDeclaration resolved) {
            for (Method method : this.allowed) {
                if (isResolvedDeclaration(resolved, method))
                    return true;
            }
        }
        return false;
    }

    @Override
    public Visitable visit(MethodCallExpr n, Void arg) {
        if (isSystemOrIOCall(n) && !Source.isCallArgument(n) && !isSpecificAllowed(n))
            return Source.removed(n);
        return super.visit(n, arg);
    }
}
