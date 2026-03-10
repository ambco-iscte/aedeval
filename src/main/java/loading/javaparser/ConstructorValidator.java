package loading.javaparser;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

public class ConstructorValidator extends ModifierVisitor<Void> {

    @Override
    public Visitable visit(ConstructorDeclaration n, Void arg) {
        TypeDeclaration<?> owner = n.findAncestor(TypeDeclaration.class).orElse(null);
        if (owner != null && !n.getNameAsString().equals(owner.getNameAsString()))
            return super.visit(n.setName(owner.getNameAsString()), arg);
        return super.visit(n, arg);
    }
}
