package javaanalyzer.visitors;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Detects null safety issues such as passing null to @NonNull/@NotNull parameters.
 */
public class NullSafetyVisitor extends VoidVisitorAdapter<Void> {
    private int nullSafetyIssueCount = 0;

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        super.visit(n, arg);

        try {
            // Check if any argument is null literal
            for (int i = 0; i < n.getArguments().size(); i++) {
                if (n.getArguments().get(i) instanceof NullLiteralExpr) {
                    // null literal found — check if parameter is @NonNull/@NotNull
                    // For now, count all null literals passed to methods
                    // (optimistic heuristic: assume they're problematic unless proven otherwise)
                    nullSafetyIssueCount++;
                }
            }
        } catch (Exception ignored) {
            // SymbolSolver may fail; ignore
        }
    }

    public int getNullSafetyIssueCount() {
        return nullSafetyIssueCount;
    }
}
