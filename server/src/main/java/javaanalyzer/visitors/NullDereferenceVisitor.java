package javaanalyzer.visitors;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.HashSet;
import java.util.Set;

/**
 * Heuristic detector for null dereference risks.
 * Detects patterns like:
 *   User user = mapper.findById(id);  // no null check
 *   user.getName();                    // immediate dereference
 */
public class NullDereferenceVisitor extends VoidVisitorAdapter<Void> {
    private int nullDereferenceRisk = 0;
    private Set<String> variablesDeclaredWithoutCheck = new HashSet<>();

    public int getNullDereferenceRisk() {
        return nullDereferenceRisk;
    }

    @Override
    public void visit(ExpressionStmt n, Void arg) {
        super.visit(n, arg);

        // Check if this statement declares a variable
        if (n.getExpression() instanceof VariableDeclarationExpr) {
            VariableDeclarationExpr varDecl = (VariableDeclarationExpr) n.getExpression();

            // Only track reference types (not primitives)
            if (!isPrimitiveType(varDecl.getElementType().asString())) {
                varDecl.getVariables().forEach(var -> {
                    // Check if initializer is a method call (likely returns null-able value)
                    if (var.getInitializer().isPresent()) {
                        if (var.getInitializer().get() instanceof MethodCallExpr) {
                            variablesDeclaredWithoutCheck.add(var.getNameAsString());
                        }
                    }
                });
            }
        }

        // Check if a declared variable is dereferenced without check
        if (n.getExpression() instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) n.getExpression();

            // Check if the scope is a variable we declared without null check
            if (call.getScope().isPresent() && call.getScope().get() instanceof NameExpr) {
                String scopeName = call.getScope().get().asNameExpr().getNameAsString();
                if (variablesDeclaredWithoutCheck.contains(scopeName)) {
                    nullDereferenceRisk++;
                    // Remove from tracking after first dereference to avoid double-counting
                    variablesDeclaredWithoutCheck.remove(scopeName);
                }
            }
        }
    }

    private boolean isPrimitiveType(String type) {
        return type.matches("(int|long|double|float|boolean|byte|short|char|void)");
    }
}
