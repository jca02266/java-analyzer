package javaanalyzer.visitors;

import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * メソッド内で 2 回以上出現する数値リテラル（マジックナンバー）を検出する。
 * 0 / 1 / -1 / 2 は一般的すぎるため除外。
 */
public class MagicNumberVisitor extends VoidVisitorAdapter<Map<String, Integer>> {

    private static final Set<String> EXCLUDED = new HashSet<>(
            Arrays.asList("0", "1", "-1", "2", "0L", "1L", "0.0", "1.0")
    );

    @Override
    public void visit(IntegerLiteralExpr n, Map<String, Integer> counts) {
        String val = n.getValue();
        if (!EXCLUDED.contains(val)) {
            counts.merge(val, 1, Integer::sum);
        }
        super.visit(n, counts);
    }

    @Override
    public void visit(LongLiteralExpr n, Map<String, Integer> counts) {
        String val = n.getValue().replaceAll("[Ll]$", "") + "L";
        if (!EXCLUDED.contains(val) && !EXCLUDED.contains(val.replace("L", ""))) {
            counts.merge(val, 1, Integer::sum);
        }
        super.visit(n, counts);
    }

    @Override
    public void visit(DoubleLiteralExpr n, Map<String, Integer> counts) {
        String val = n.getValue();
        if (!EXCLUDED.contains(val)) {
            counts.merge(val, 1, Integer::sum);
        }
        super.visit(n, counts);
    }

    /** 収集した counts から 2 回以上出現したリテラルを返す */
    public static List<String> repeated(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }
}
