package javaanalyzer.visitors;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.Optional;

/**
 * ラムダ式・メソッド参照・メソッドチェーン深度を計測する。
 * Optional.get() 直呼びは型解決（Phase4）が必要なため現在は計測しない。
 */
public class LambdaStreamVisitor extends VoidVisitorAdapter<Void> {

    private int lambdaCount = 0;
    private int maxMethodChainDepth = 0;
    private int methodReferenceCount = 0;

    public int getLambdaCount() { return lambdaCount; }
    public int getMaxMethodChainDepth() { return maxMethodChainDepth; }
    public int getMethodReferenceCount() { return methodReferenceCount; }

    @Override
    public void visit(LambdaExpr n, Void arg) {
        lambdaCount++;
        super.visit(n, arg);
    }

    @Override
    public void visit(MethodReferenceExpr n, Void arg) {
        methodReferenceCount++;
        super.visit(n, arg);
    }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        // チェーンの末尾（親が MethodCallExpr のスコープではない）でのみ計測して二重カウントを防ぐ
        if (!isInnerScope(n)) {
            int depth = chainDepth(n);
            if (depth > maxMethodChainDepth) {
                maxMethodChainDepth = depth;
            }
        }
        super.visit(n, arg);
    }

    /** この MethodCallExpr が親 MethodCallExpr のスコープである（チェーンの途中）かどうか */
    private boolean isInnerScope(MethodCallExpr n) {
        return n.getParentNode()
                .filter(p -> p instanceof MethodCallExpr)
                .map(p -> ((MethodCallExpr) p).getScope()
                        .filter(s -> s == n)
                        .isPresent())
                .orElse(false);
    }

    /** MethodCallExpr のスコープを辿ってチェーン長を返す */
    private int chainDepth(MethodCallExpr expr) {
        int depth = 1;
        Optional<Expression> scope = expr.getScope();
        while (scope.isPresent() && scope.get() instanceof MethodCallExpr) {
            depth++;
            scope = ((MethodCallExpr) scope.get()).getScope();
        }
        return depth;
    }
}
