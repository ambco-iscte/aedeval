package loading.javaparser;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

public class SystemCallRemover extends ModifierVisitor<Void> {

    @Override
    public Visitable visit(ExpressionStmt n, Void arg) {
        if (n != null) {
            Expression expr = n.getExpression();
            if (expr.isMethodCallExpr() && expr.toString().startsWith("System.")) {
                expr.remove();
                /*
                ((MethodCallExpr) expr).getScope().ifPresent(expression -> {
                    if (expression.isNameExpr())
                        expression.asNameExpr().setName("// " + expression.asNameExpr().getNameAsString());
                    else if (expression.isFieldAccessExpr() && expression.asFieldAccessExpr().hasScope())
                        expression.asFieldAccessExpr().setScope(new NameExpr("// " + expression.asFieldAccessExpr().getScope()));
                });
                n.setExpression(expr);
                 */
            }
        }
        return super.visit(n, arg);
    }
}
