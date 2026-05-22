package javaanalyzer.visitors;

import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * if/for/while/do/switch/try の各ブロックをネスト 1 段として最大深度を計測する。
 * else-if は AST 上で入れ子の IfStmt となるため、同深度扱いにならない点は既知の制限。
 */
public class NestDepthVisitor extends VoidVisitorAdapter<Void> {

    private int current = 0;
    private int max = 0;

    public int getMax() {
        return max;
    }

    private void enter() {
        current++;
        if (current > max) {
            max = current;
        }
    }

    private void exit() {
        current--;
    }

    @Override
    public void visit(IfStmt n, Void arg) {
        enter();
        super.visit(n, arg);
        exit();
    }

    @Override
    public void visit(ForStmt n, Void arg) {
        enter();
        super.visit(n, arg);
        exit();
    }

    @Override
    public void visit(ForEachStmt n, Void arg) {
        enter();
        super.visit(n, arg);
        exit();
    }

    @Override
    public void visit(WhileStmt n, Void arg) {
        enter();
        super.visit(n, arg);
        exit();
    }

    @Override
    public void visit(DoStmt n, Void arg) {
        enter();
        super.visit(n, arg);
        exit();
    }

    @Override
    public void visit(SwitchStmt n, Void arg) {
        enter();
        super.visit(n, arg);
        exit();
    }

    @Override
    public void visit(TryStmt n, Void arg) {
        enter();
        super.visit(n, arg);
        exit();
    }
}
