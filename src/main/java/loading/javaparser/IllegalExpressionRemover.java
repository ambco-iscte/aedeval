package loading.javaparser;

import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import loading.Source;
import static extensions.Extensions.tryOrElse;
import static extensions.Extensions.contains;

public class IllegalExpressionRemover extends ModifierVisitor<Void> {

    private final String[] allowedPackages;

    public IllegalExpressionRemover(String[] allowedPackages) {
        this.allowedPackages = allowedPackages;
    }

    private boolean isProhibitedPackage(String name) {
        return name != null && !name.isEmpty() && !contains(allowedPackages, name);
    }

    private boolean isProhibitedType(ResolvedTypeDeclaration type) {
        return type != null && isProhibitedPackage(type.getPackageName());
    }

    private boolean isProhibitedType(ResolvedType type) {
        if (type.isArray())
            return isProhibitedType(type.asArrayType().getComponentType());
        if (type.isReferenceType())
            return isProhibitedType(type.asReferenceType().getTypeDeclaration().orElse(null));
        return false;
    }

    private boolean isProhibitedField(FieldAccessExpr field) {
        return tryOrElse(() -> isProhibitedType(field.resolve().asField().declaringType()), false);
    }

    private boolean isProhibitedType(Type type) {
        return tryOrElse(() -> isProhibitedType(type.resolve()), false);
    }

    private boolean isProhibitedTypeName(NameExpr name) {
        return tryOrElse(() -> isProhibitedType(name.calculateResolvedType()), false);
    }

    private boolean isProhibitedExpression(Expression expression) {
        if (expression == null)
            return false;
        return
            (expression.isFieldAccessExpr() && isProhibitedField(expression.asFieldAccessExpr())) ||
            (expression.isNameExpr() && isProhibitedTypeName(expression.asNameExpr()));
    }

    @Override
    public Visitable visit(MethodCallExpr n, Void arg) {
        if (isProhibitedExpression(n.getScope().orElse(null)))
            return Source.removed(n);
        else {
            for (Expression argument : n.getArguments()) {
                if (isProhibitedExpression(argument))
                    return Source.removed(n);
            }
        }
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(ObjectCreationExpr n, Void arg) {
        if (isProhibitedType(n.getType()) || isProhibitedExpression(n.getScope().orElse(null)))
            return Source.removed(n);
        else {
            for (Expression argument : n.getArguments()) {
                if (isProhibitedExpression(argument))
                    return Source.removed(n);
            }
        }
        return super.visit(n, arg);
    }

    /*
    @Override
    public Visitable visit(VariableDeclarator n, Void arg) {
        if (isProhibitedExpression(n.getInitializer().orElse(null)) || isProhibitedType(n.getType()))
            return n.removeInitializer();
        return super.visit(n, arg);
    }
     */
}
