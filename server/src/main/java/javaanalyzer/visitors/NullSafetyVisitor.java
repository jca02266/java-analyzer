package javaanalyzer.visitors;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Detects null safety issues:
 * - null literals passed to methods
 * - Optional.orElse(null) usage
 */
public class NullSafetyVisitor extends VoidVisitorAdapter<Void> {
    private int nullLiteralCount = 0;
    private int optionalOrElseNull = 0;

    public int getNullLiteralCount() {
        return nullLiteralCount;
    }

    public int getOptionalOrElseNull() {
        return optionalOrElseNull;
    }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        super.visit(n, arg);

        String methodName = n.getNameAsString();

        // Check for Optional.orElse(null)
        if ("orElse".equals(methodName) && n.getArguments().size() == 1) {
            if (n.getArguments().get(0) instanceof NullLiteralExpr) {
                optionalOrElseNull++;
            }
        }

        // Check null literals passed to methods
        try {
            for (int i = 0; i < n.getArguments().size(); i++) {
                if (n.getArguments().get(i) instanceof NullLiteralExpr) {
                    nullLiteralCount++;
                }
            }
        } catch (Exception ignored) {
            // Continue on any error
        }
    }
}
