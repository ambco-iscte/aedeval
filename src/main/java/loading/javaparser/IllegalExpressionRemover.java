package loading.javaparser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import loading.Source;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static extensions.Extensions.contains;
import static extensions.Extensions.tryOrElse;

public class IllegalExpressionRemover extends ModifierVisitor<Void> {

    private final TypeDeclaration<?> self;
    private final String[] allowedPackages;
    private final Map<String, HashSet<Node>> prohibitedUsages = new HashMap<>();

    public IllegalExpressionRemover(TypeDeclaration<?> self, String[] allowedPackages) {
        this.self = self;
        this.allowedPackages = allowedPackages;
    }

    public Map<String, Node[]> getProhibitedUsages() {
        Map<String, Node[]> usages = new HashMap<>();
        for (String pkg : prohibitedUsages.keySet()) {
            usages.put(pkg, prohibitedUsages.get(pkg).toArray(new Node[0]));
        }
        return usages;
    }

    private boolean isProhibitedPackage(Node location, String name) {
        boolean prohibited = name != null && !name.isEmpty() && !contains(allowedPackages, name);
        if (prohibited) {
            prohibitedUsages.putIfAbsent(name, new HashSet<>());
            prohibitedUsages.get(name).add(location);
        }
        return prohibited;
    }

    private boolean isProhibitedType(Node location, ResolvedTypeDeclaration type) {
        return type != null && tryOrElse(() -> self != null && self.resolve() != type, true) && isProhibitedPackage(location, type.getPackageName());
    }

    private boolean isProhibitedType(Node location, ResolvedType type) {
        if (type.isArray())
            return isProhibitedType(location, type.asArrayType().getComponentType());
        if (type.isReferenceType())
            return isProhibitedType(location, type.asReferenceType().getTypeDeclaration().orElse(null));
        return false;
    }

    private boolean isProhibitedType(Node location, Type type) {
        return tryOrElse(() -> isProhibitedType(location, type.resolve()), false);
    }

    private boolean isProhibitedField(FieldAccessExpr field) {
        if (Source.isCallArgument(field))
            return false;
        return tryOrElse(() -> isProhibitedType(field, field.resolve().asField().declaringType()), false);
    }

    private boolean isProhibitedTypeName(NameExpr name) {
        if (Source.isCallArgument(name))
            return false;
        return tryOrElse(() -> isProhibitedType(name, name.calculateResolvedType()), false);
    }

    private boolean isProhibitedExpression(Node location, Expression expression) {
        if (expression == null || Source.isCallArgument(expression))
            return false;
        return
            // tryOrElse(() -> isProhibitedType(location, expression.calculateResolvedType()), false) ||
            (expression.isMethodCallExpr() && isProhibitedMethodCall(location, expression.asMethodCallExpr())) ||
            (expression.isObjectCreationExpr() && isProhibitedConstructorCall(location, expression.asObjectCreationExpr()));

    }

    private boolean isProhibitedMethodCall(Node location, MethodCallExpr n) {
        if (isProhibitedExpression(location, n.getScope().orElse(null)))
            return true;
        else {
            for (Expression argument : n.getArguments()) {
                if (isProhibitedExpression(location, argument))
                    return true;
            }
        }
        return tryOrElse(() -> isProhibitedType(location, n.calculateResolvedType()), false);
    }

    private boolean isProhibitedConstructorCall(Node location, ObjectCreationExpr n) {
        if (isProhibitedExpression(location, n.getScope().orElse(null)))
            return true;
        else {
            for (Expression argument : n.getArguments()) {
                if (isProhibitedExpression(location, argument))
                    return true;
            }
        }
        return isProhibitedType(location, n.getType());
    }

    @Override
    public Visitable visit(MethodCallExpr n, Void arg) {
        if (isProhibitedMethodCall(n, n) && !Source.isCallArgument(n))
            return Source.removed(n);
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(ObjectCreationExpr n, Void arg) {
        if (isProhibitedConstructorCall(n, n) && !Source.isCallArgument(n))
            return Source.removed(n);
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
