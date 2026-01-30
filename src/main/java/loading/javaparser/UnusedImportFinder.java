package loading.javaparser;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.ArrayList;
import java.util.List;

public class UnusedImportFinder extends ModifierVisitor<Void> {

    private final List<ImportDeclaration> imports = new ArrayList<>();

    private final List<ImportDeclaration> used = new ArrayList<>();

    private ImportDeclaration find(String name) {
        for (ImportDeclaration it : imports) {
            if (it.getNameAsString().equals(name) || it.getName().getIdentifier().equals(name))
                return it;
        }
        return null;
    }

    public List<ImportDeclaration> getUnusedImports() {
        return imports.stream().filter(it -> !used.contains(it)).toList();
    }

    @Override
    public Node visit(ImportDeclaration n, Void arg) {
        imports.add(n);
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(NameExpr n, Void arg) {
        ImportDeclaration match = find(n.toString());
        if (match != null)
            used.add(match);
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(Name n, Void arg) {
        ImportDeclaration match = find(n.asString());
        if (match != null && n.findAncestor(ImportDeclaration.class).isEmpty())
            used.add(match);
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(MethodCallExpr n, Void arg) {
        ImportDeclaration match = find(n.getNameAsString());
        if (match != null)
            used.add(match);
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(TypeExpr n, Void arg) {
        System.out.println("Visited type expr: " + n.getTypeAsString());
        ImportDeclaration match = find(n.getTypeAsString());
        if (match != null)
            used.add(match);
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(VariableDeclarator n, Void arg) {
        ImportDeclaration match = find(n.getTypeAsString());
        if (match != null)
            used.add(match);
        else {
            ResolvedType type = n.getType().resolve();
            if (type.isReferenceType()) {
                match = find(type.asReferenceType().getQualifiedName());
                if (match != null)
                    used.add(match);
            }
        }
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(MethodDeclaration n, Void arg) {
        ImportDeclaration match = find(n.getTypeAsString());
        if (match != null)
            used.add(match);
        else {
            ResolvedType type = n.getType().resolve();
            if (type.isReferenceType()) {
                match = find(type.asReferenceType().getQualifiedName());
                if (match != null)
                    used.add(match);
            }
        }
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(ArrayCreationExpr n, Void arg) {
        ImportDeclaration match = find(n.getElementType().asString());
        if (match != null)
            used.add(match);
        else {
            ResolvedType type = n.getElementType().resolve();
            if (type.isReferenceType()) {
                match = find(type.asReferenceType().getQualifiedName());
                if (match != null)
                    used.add(match);
            }
        }
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(ObjectCreationExpr n, Void arg) {
        ImportDeclaration match = find(n.getTypeAsString());
        if (match != null)
            used.add(match);
        else {
            ResolvedType type = n.getType().resolve();
            if (type.isReferenceType()) {
                match = find(type.asReferenceType().getQualifiedName());
                if (match != null)
                    used.add(match);
            }
        }
        return super.visit(n, arg);
    }
}
