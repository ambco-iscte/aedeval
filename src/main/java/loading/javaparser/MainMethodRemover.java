package loading.javaparser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import loading.Source;

public class MainMethodRemover extends ModifierVisitor<Void> {

    @Override
    public Visitable visit(MethodDeclaration n, Void arg) {
        if (n != null && (Source.isMainMethod(n) || Source.isInstanceMainMethod(n))) {
            n.remove();
        }
        return super.visit(n, arg);
    }
}
