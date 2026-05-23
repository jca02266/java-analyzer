package javaanalyzer.visitors;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Cognitive Complexity (SonarSource 仕様) を計算する。
 * ネスト深度を考慮した複雑度指標。
 * - 制御構造（if/for など）: +1 + nestLevel
 * - else/else if: +1（nestLevel 加算なし）
 * - 論理演算子の変化: +1
 */
public class CognitiveComplexityVisitor extends VoidVisitorAdapter<Void> {
    private int score = 0;
    private int nestLevel = 0;
    private BinaryExpr.Operator lastLogicalOp = null;

    public int getScore() {
        return score;
    }

    @Override
    public void visit(IfStmt n, Void arg) {
        score += 1 + nestLevel;
        nestLevel++;
        n.getCondition().accept(this, arg);
        n.getThenStmt().accept(this, arg);
        nestLevel--;

        n.getElseStmt().ifPresent(elseStmt -> {
            score += 1;
            if (elseStmt instanceof IfStmt) {
                IfStmt elseIf = (IfStmt) elseStmt;
                nestLevel++;
                elseIf.getCondition().accept(this, arg);
                elseIf.getThenStmt().accept(this, arg);
                nestLevel--;
                elseIf.getElseStmt().ifPresent(e -> {
                    score += 1;
                    e.accept(this, arg);
                });
            } else {
                elseStmt.accept(this, arg);
            }
        });
    }

    @Override
    public void visit(ForStmt n, Void arg) {
        score += 1 + nestLevel;
        nestLevel++;
        super.visit(n, arg);
        nestLevel--;
    }

    @Override
    public void visit(ForEachStmt n, Void arg) {
        score += 1 + nestLevel;
        nestLevel++;
        super.visit(n, arg);
        nestLevel--;
    }

    @Override
    public void visit(WhileStmt n, Void arg) {
        score += 1 + nestLevel;
        nestLevel++;
        super.visit(n, arg);
        nestLevel--;
    }

    @Override
    public void visit(DoStmt n, Void arg) {
        score += 1 + nestLevel;
        nestLevel++;
        super.visit(n, arg);
        nestLevel--;
    }

    @Override
    public void visit(SwitchStmt n, Void arg) {
        score += 1 + nestLevel;
        nestLevel++;
        super.visit(n, arg);
        nestLevel--;
    }

    @Override
    public void visit(CatchClause n, Void arg) {
        score += 1 + nestLevel;
        nestLevel++;
        super.visit(n, arg);
        nestLevel--;
    }

    @Override
    public void visit(LambdaExpr n, Void arg) {
        score += 1;
        nestLevel++;
        super.visit(n, arg);
        nestLevel--;
    }

    @Override
    public void visit(BinaryExpr n, Void arg) {
        if (n.getOperator() == BinaryExpr.Operator.AND || n.getOperator() == BinaryExpr.Operator.OR) {
            if (n.getOperator() != lastLogicalOp) {
                score++;
                lastLogicalOp = n.getOperator();
            }
        } else {
            lastLogicalOp = null;
        }
        super.visit(n, arg);
    }
}
