package loading.javaparser;

import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class ControlStructureBracketer extends VoidVisitorAdapter<Void> {

    @Override
    public void visit(IfStmt n, Void arg) {
        super.visit(n, arg);

        if (n != null && n.getThenStmt() != null && !n.getThenStmt().isBlockStmt()) {
            Statement thenStmt = n.getThenStmt();
            BlockStmt block = new BlockStmt();
            n.setThenStmt(block);
            if (thenStmt != null)
                block.addStatement(thenStmt);
        }

        if (n != null && n.getElseStmt().isPresent() && !n.getElseStmt().get().isBlockStmt()) {
            Statement elseStmt = n.getElseStmt().get();
            BlockStmt block = new BlockStmt();
            n.setElseStmt(block);
            block.addStatement(elseStmt);
        }
    }

    @Override
    public void visit(WhileStmt n, Void arg) {
        super.visit(n, arg);
        if (n != null && n.getBody() != null && !n.getBody().isBlockStmt()) {
            Statement body = n.getBody();
            BlockStmt block = new BlockStmt();
            n.setBody(block);
            if (body != null)
                block.addStatement(body);
        }
    }

    @Override
    public void visit(DoStmt n, Void arg) {
        super.visit(n, arg);
        if (n != null && n.getBody() != null && !n.getBody().isBlockStmt()) {
            Statement body = n.getBody();
            BlockStmt block = new BlockStmt();
            n.setBody(block);
            if (body != null)
                block.addStatement(body);
        }
    }

    @Override
    public void visit(ForStmt n, Void arg) {
        super.visit(n, arg);
        if (n != null && n.getBody() != null && !n.getBody().isBlockStmt()) {
            Statement body = n.getBody();
            BlockStmt block = new BlockStmt();
            n.setBody(block);
            if (body != null)
                block.addStatement(body);
        }
    }

    @Override
    public void visit(ForEachStmt n, Void arg) {
        super.visit(n, arg);
        if (n != null && n.getBody() != null && !n.getBody().isBlockStmt()) {
            Statement body = n.getBody();
            BlockStmt block = new BlockStmt();
            n.setBody(block);
            if (body != null)
                block.addStatement(body);
        }
    }
}
