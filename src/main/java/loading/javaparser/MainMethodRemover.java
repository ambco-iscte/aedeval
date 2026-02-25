package loading.javaparser;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import loading.Source;

public class MainMethodRemover extends ModifierVisitor<Void> {

    @Override
    public Visitable visit(MethodDeclaration n, Void arg) {
        if (n != null && Source.isMainMethod(n))
            return Source.removed(n);
        return super.visit(n, arg);
    }
}
