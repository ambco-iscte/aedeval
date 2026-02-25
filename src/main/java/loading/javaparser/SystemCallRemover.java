package loading.javaparser;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import loading.Source;

public class SystemCallRemover extends ModifierVisitor<Void> {

    private static boolean isSystemOrIOCall(Expression expression) {
        if (expression == null)
            throw new IllegalArgumentException("Cannot check if a null expression is a System or IO call!");

        if (!expression.isMethodCallExpr())
            return false;

        MethodCallExpr call = expression.asMethodCallExpr();
        if (call.getScope().isEmpty())
            return false;

        Expression scope = call.getScope().get();
        if (scope.isFieldAccessExpr()) {
            FieldAccessExpr access = scope.asFieldAccessExpr(); // System.out
            Expression owner = access.getScope();
            return owner.isNameExpr() && owner.asNameExpr().getName().getIdentifier().equals("System");
        }

        return scope.isNameExpr() && scope.asNameExpr().getName().getIdentifier().equals("IO");
    }

    @Override
    public Visitable visit(MethodCallExpr n, Void arg) {
        if (n != null && isSystemOrIOCall(n))
            return Source.removed(n);
        return super.visit(n, arg);
    }
}
