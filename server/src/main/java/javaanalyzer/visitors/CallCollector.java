package javaanalyzer.visitors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import javaanalyzer.metrics.CallEdge;

import java.util.ArrayList;
import java.util.List;

/**
 * CU 内の全メソッド呼び出しを収集し、CallEdge リストを返す。
 * キー形式: "ClassName#methodName"
 */
public class CallCollector {

    public List<CallEdge> collect(CompilationUnit cu) {
        List<CallEdge> edges = new ArrayList<>();
        for (TypeDeclaration<?> type : cu.getTypes()) {
            String className = type.getNameAsString();
            for (MethodDeclaration m : type.getMethods()) {
                collectFromCallable(m, className, m.getNameAsString(), edges);
            }
            if (type instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) {
                for (ConstructorDeclaration ctor :
                        ((com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) type).getConstructors()) {
                    collectFromCallable(ctor, className, "<init>", edges);
                }
            }
        }
        return edges;
    }

    private void collectFromCallable(CallableDeclaration<?> callable,
                                     String className, String methodName,
                                     List<CallEdge> edges) {
        String fromKey = className + "#" + methodName;
        callable.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                CallEdge edge = new CallEdge();
                edge.from = fromKey;
                edge.resolved = false;
                try {
                    ResolvedMethodDeclaration resolved = n.resolve();
                    String toClass = resolved.getClassName();
                    edge.to = toClass + "#" + resolved.getName();
                    edge.resolved = true;
                } catch (Exception e) {
                    // ヒューリスティック: スコープ.メソッド名
                    String scope = n.getScope().map(s -> s.toString()).orElse("?");
                    edge.to = scope + "#" + n.getNameAsString();
                }
                edges.add(edge);
            }
        }, null);
    }
}
