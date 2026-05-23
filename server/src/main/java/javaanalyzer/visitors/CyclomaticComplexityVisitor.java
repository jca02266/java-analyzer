package javaanalyzer.visitors;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Cyclomatic Complexity (McCabe's metric) を計算する。
 * 初期値 1、分岐点（if/for/while など）ごとに +1。
 */
public class CyclomaticComplexityVisitor extends VoidVisitorAdapter<Void> {
    private int complexity = 1;

    public int getComplexity() {
        return complexity;
    }

    @Override
    public void visit(IfStmt n, Void arg) {
        complexity++;
        super.visit(n, arg);
    }

    @Override
    public void visit(ForStmt n, Void arg) {
        complexity++;
        super.visit(n, arg);
    }

    @Override
    public void visit(ForEachStmt n, Void arg) {
        complexity++;
        super.visit(n, arg);
    }

    @Override
    public void visit(WhileStmt n, Void arg) {
        complexity++;
        super.visit(n, arg);
    }

    @Override
    public void visit(DoStmt n, Void arg) {
        complexity++;
        super.visit(n, arg);
    }

    @Override
    public void visit(SwitchStmt n, Void arg) {
        complexity++;
        super.visit(n, arg);
    }

    @Override
    public void visit(SwitchEntry n, Void arg) {
        if (!n.getLabels().isEmpty()) {
            complexity++;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(ConditionalExpr n, Void arg) {
        complexity++;
        super.visit(n, arg);
    }

    @Override
    public void visit(BinaryExpr n, Void arg) {
        if (n.getOperator() == BinaryExpr.Operator.AND || n.getOperator() == BinaryExpr.Operator.OR) {
            complexity++;
        }
        super.visit(n, arg);
    }
}
