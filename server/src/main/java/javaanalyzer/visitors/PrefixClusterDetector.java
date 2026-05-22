package javaanalyzer.visitors;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import javaanalyzer.metrics.PrefixCluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * フィールド名・定数名のうち UPPER_CASE_PREFIX を持つものをクラスタリングし、
 * 3 名以上のクラスタを PrefixCluster として返す。
 * 例: MAX_SIZE, MAX_COUNT, MAX_RETRY → prefix "MAX"
 */
public class PrefixClusterDetector {

    private static final Pattern UPPER_PREFIX = Pattern.compile("^([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*)_[A-Z]");
    private static final int MIN_CLUSTER_SIZE = 3;

    public List<PrefixCluster> detect(TypeDeclaration<?> type) {
        String className = type.getNameAsString();
        Map<String, List<String>> clusters = new HashMap<>();

        type.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(FieldDeclaration n, Void arg) {
                super.visit(n, arg);
                for (VariableDeclarator v : n.getVariables()) {
                    String name = v.getNameAsString();
                    Matcher m = UPPER_PREFIX.matcher(name);
                    if (m.find()) {
                        String prefix = m.group(1);
                        clusters.computeIfAbsent(prefix, k -> new ArrayList<>()).add(name);
                    }
                }
            }
        }, null);

        List<PrefixCluster> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : clusters.entrySet()) {
            if (e.getValue().size() >= MIN_CLUSTER_SIZE) {
                PrefixCluster pc = new PrefixCluster();
                pc.prefix      = e.getKey();
                pc.identifiers = e.getValue();
                pc.className   = className;
                result.add(pc);
            }
        }
        return result;
    }
}
