package javaanalyzer.visitors;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import javaanalyzer.metrics.AssignmentBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * メソッド本体の直下のステートメント列を走査し、
 * 5 行以上連続する代入系ステートメント（変数宣言 / 代入式）のブロックを検出する。
 */
public class AssignmentBlockVisitor {

    private static final int MIN_BLOCK_SIZE = 5;

    public List<AssignmentBlock> analyze(MethodDeclaration method) {
        return method.getBody()
                .map(body -> analyzeBlock(body.getStatements()))
                .orElse(new ArrayList<>());
    }

    public List<AssignmentBlock> analyze(ConstructorDeclaration ctor) {
        return analyzeBlock(ctor.getBody().getStatements());
    }

    private List<AssignmentBlock> analyzeBlock(NodeList<Statement> stmts) {
        List<AssignmentBlock> result = new ArrayList<>();
        int runStart = -1;
        int runEnd   = -1;
        int varDeclCount  = 0;
        int assignCount   = 0;
        int runTotal      = 0;

        for (Statement stmt : stmts) {
            StatKind kind = classify(stmt);
            if (kind != StatKind.OTHER) {
                if (runStart == -1) runStart = startLine(stmt);
                runEnd = endLine(stmt);
                runTotal++;
                if (kind == StatKind.VAR_DECL) varDeclCount++;
                if (kind == StatKind.ASSIGN)    assignCount++;
            } else {
                if (runTotal >= MIN_BLOCK_SIZE) {
                    result.add(build(runStart, runEnd, runTotal, varDeclCount, assignCount));
                }
                runStart = -1; runTotal = 0; varDeclCount = 0; assignCount = 0;
            }
        }
        if (runTotal >= MIN_BLOCK_SIZE) {
            result.add(build(runStart, runEnd, runTotal, varDeclCount, assignCount));
        }
        return result;
    }

    private AssignmentBlock build(int start, int end, int total, int varDecls, int assigns) {
        AssignmentBlock b = new AssignmentBlock();
        b.startLine = start;
        b.endLine   = end;
        b.count     = total;
        b.shape     = shape(total, varDecls, assigns);
        return b;
    }

    private String shape(int total, int varDecls, int assigns) {
        if (varDecls == total)                   return "var-decl";
        if (assigns  == total)                   return "assign";
        if ((double) varDecls / total >= 0.7)    return "mostly-var-decl";
        if ((double) assigns  / total >= 0.7)    return "mostly-assign";
        return "mixed";
    }

    private StatKind classify(Statement stmt) {
        if (!(stmt instanceof ExpressionStmt)) return StatKind.OTHER;
        Expression expr = ((ExpressionStmt) stmt).getExpression();
        if (expr instanceof VariableDeclarationExpr) return StatKind.VAR_DECL;
        if (expr instanceof AssignExpr)               return StatKind.ASSIGN;
        return StatKind.OTHER;
    }

    private int startLine(Statement stmt) {
        return stmt.getBegin().map(p -> p.line).orElse(0);
    }

    private int endLine(Statement stmt) {
        return stmt.getEnd().map(p -> p.line).orElse(0);
    }

    private enum StatKind { VAR_DECL, ASSIGN, OTHER }
}
