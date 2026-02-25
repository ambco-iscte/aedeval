package loading.javaparser;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class ClassModifierRemover extends ModifierVisitor<Void> {

    private final Modifier.Keyword[] remove;

    public ClassModifierRemover(Modifier.Keyword[] remove) {
        if (remove == null)
            this.remove = new Modifier.Keyword[0];
        else this.remove = remove;
    }

    public ClassModifierRemover(Modifier.Keyword remove) {
        if (remove == null)
            this.remove = new Modifier.Keyword[0];
        else this.remove = new Modifier.Keyword[] { remove };
    }

    @Override
    public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
        if (n.isTopLevelType() || !n.isInnerClass())
            return super.visit(n.removeModifier(remove), arg);
        return super.visit(n, arg);
    }
}
