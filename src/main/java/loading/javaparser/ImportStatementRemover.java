package loading.javaparser;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.util.List;

public class ImportStatementRemover extends ModifierVisitor<Void> {

    private final List<ImportDeclaration> unused;

    public ImportStatementRemover(List<ImportDeclaration> unused) {
        this.unused = unused;
    }

    @Override
    public Node visit(ImportDeclaration n, Void arg) {
        if (unused.contains(n))
            n.remove();
        return super.visit(n, arg);
    }
}
