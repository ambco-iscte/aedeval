package evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import extensions.Extensions;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class Descriptor {

    private static class TestMethodDescriptor extends VoidVisitorAdapter<Void> {

        public Map<MethodDeclaration, String> descriptions = new HashMap<>();

        private MethodDeclaration current = null;

        private Map<MethodDeclaration, Set<MethodCallExpr>> visited = new HashMap<>();

        private String describeInvocation(MethodCallExpr invoke) {
            NodeList<Expression> arguments = invoke.getArguments();
            String name = arguments.get(0).toString();
            String caller = arguments.get(1).toString();

            if (arguments.get(1) instanceof NameExpr var) {
                MethodDeclaration method = invoke.findAncestor(MethodDeclaration.class).get();
                method.findFirst(VariableDeclarationExpr.class, variableDeclarationExpr -> {
                    for (VariableDeclarator declarator : variableDeclarationExpr.getVariables()) {
                        if (declarator.getNameAsString().equals(var.getNameAsString()))
                            return true;
                    }
                    return false;
                }).ifPresent(variableDeclarationExpr -> {
                    if (!descriptions.containsKey(current))
                        descriptions.put(current, "\t" + variableDeclarationExpr);
                    else
                        descriptions.put(current, descriptions.get(current) + System.lineSeparator() + "\t" + variableDeclarationExpr);
                });
            }

            String args = Extensions.joinToString(arguments.subList(2, arguments.size()));
            setVisited(invoke);
            if (caller.equals("null"))
                return name + "(" + args + ")";
            return caller + "." + name + "(" + args + ")";
        }

        private String describeAssertion(MethodCallExpr assertion) {
            MethodCallExpr invocation = (MethodCallExpr) assertion.getScope().get();
            String msg = switch (assertion.getNameAsString()) {
                case "assertThrows" -> "throws " + assertion.getArgument(0).toString();
                case "assertDoesNotThrow" -> "does not throw any exception(s)";
                case "assertEquals" -> "equals " + assertion.getArgument(0).toString();
                case "assertEqualsAny" -> "equals one of [" + Extensions.joinToString(assertion.getArguments()) +  "]";
                case "assertEqualsTimed" -> "";
                case "assertEqualsAnyTimed" -> "";
                default -> throw new IllegalStateException("Unexpected assertion: " + assertion.getNameAsString());
            };
            setVisited(assertion);
            setVisited(invocation);
            return "assert " + describeInvocation(invocation) + " " + msg;
        }

        private void setVisited(MethodCallExpr expr) {
            if (current == null)
                return;
            if (!visited.containsKey(current))
                visited.put(current, new HashSet<>());
            visited.get(current).add(expr);
        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            if (n.isAnnotationPresent("Test"))
                current = n;
            else
                current = null;
            visited.put(current, new HashSet<>());
            super.visit(n, arg);
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) { // TODO what if it's inside a loop etc
            if (current != null && !visited.get(current).contains(n)) {
                String description = null;
                if (n.getNameAsString().equals("invoke"))
                    description = describeInvocation(n);
                else if (n.getNameAsString().startsWith("assert"))
                    description = describeAssertion(n);

                if (description != null) {
                    if (!descriptions.containsKey(current))
                        descriptions.put(current, "\t" + description);
                    else
                        descriptions.put(current, descriptions.get(current) + System.lineSeparator() + "\t" + description);
                }
            }
            super.visit(n, arg);
        }
    }

    public static String describe(File testerSource) throws FileNotFoundException {
        CompilationUnit unit = StaticJavaParser.parse(testerSource);

        TestMethodDescriptor descriptor = new TestMethodDescriptor();
        descriptor.visit(unit, null);

        StringBuilder description = new StringBuilder();
        for (MethodDeclaration n : descriptor.descriptions.keySet())
            description.append("[").append(n.getNameAsString()).append("]").append(System.lineSeparator()).append(descriptor.descriptions.get(n)).append(System.lineSeparator()).append(System.lineSeparator());
        return description.toString().trim();
    }

    public static void main(String[] args) throws FileNotFoundException {
        /*
        CompilationUnit unit = StaticJavaParser.parse("""
                public class Hello {
                    @Test
                    public void test() {
                        invoke(enqueue, queue, "World").assertDoesNotThrow();
                    }
                }
                """);
        System.out.println(unit.findAll(MethodCallExpr.class));
        */
        System.out.println(describe(new File("src/main/java/aed/testers/TestSubmission4.java")));
    }
}
