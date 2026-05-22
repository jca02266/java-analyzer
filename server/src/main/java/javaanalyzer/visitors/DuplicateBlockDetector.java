package javaanalyzer.visitors;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import javaanalyzer.metrics.DuplicateBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ワークスペース全体を走査し、3 ステートメント以上の同一シーケンスが
 * 2 箇所以上に出現する「重複ブロック」を検出する。
 * 正規化: 変数名・リテラルを削除して構造のみ比較。
 */
public class DuplicateBlockDetector {

    private static final int MIN_STMTS = 3;

    /** key: 正規化ハッシュ → locations ("ClassName#method:line") */
    private final Map<String, List<String>> index = new HashMap<>();
    /** key: 正規化ハッシュ → (stmtCount, preview) */
    private final Map<String, int[]> countMap   = new HashMap<>();
    private final Map<String, String> previewMap = new HashMap<>();

    public void index(TypeDeclaration<?> type) {
        String className = type.getNameAsString();
        for (MethodDeclaration m : type.getMethods()) {
            indexCallable(m, className, m.getNameAsString());
        }
        if (type instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) {
            for (ConstructorDeclaration ctor :
                    ((com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) type).getConstructors()) {
                indexCallable(ctor, className, "<init>");
            }
        }
    }

    private void indexCallable(CallableDeclaration<?> callable, String className, String methodName) {
        List<Statement> stmts = getStatements(callable);
        if (stmts == null || stmts.size() < MIN_STMTS) return;

        String locationPrefix = className + "#" + methodName + ":";

        for (int i = 0; i <= stmts.size() - MIN_STMTS; i++) {
            for (int len = MIN_STMTS; len <= stmts.size() - i; len++) {
                List<Statement> window = stmts.subList(i, i + len);
                String key = normalizeWindow(window);
                String location = locationPrefix + startLine(stmts.get(i));

                index.computeIfAbsent(key, k -> new ArrayList<>()).add(location);
                if (!countMap.containsKey(key)) {
                    countMap.put(key, new int[]{len});
                    previewMap.put(key, truncate(stmts.get(i).toString(), 80));
                }
            }
        }
    }

    public List<DuplicateBlock> getResults() {
        List<DuplicateBlock> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : index.entrySet()) {
            List<String> locs = e.getValue();
            if (locs.size() < 2) continue;
            String key = e.getKey();
            DuplicateBlock db = new DuplicateBlock();
            db.stmtCount = countMap.get(key)[0];
            db.preview   = previewMap.get(key);
            // 重複のない場所リスト
            db.locations = new ArrayList<>(new java.util.LinkedHashSet<>(locs));
            result.add(db);
        }
        // stmtCount 降順でソート
        result.sort((a, b) -> Integer.compare(b.stmtCount, a.stmtCount));
        // 最大 50 件に絞る（巨大レポート対策）
        return result.size() > 50 ? result.subList(0, 50) : result;
    }

    private List<Statement> getStatements(CallableDeclaration<?> callable) {
        if (callable instanceof MethodDeclaration) {
            return ((MethodDeclaration) callable).getBody()
                    .map(BlockStmt::getStatements)
                    .map(nl -> (List<Statement>) new ArrayList<>(nl))
                    .orElse(null);
        }
        if (callable instanceof ConstructorDeclaration) {
            return new ArrayList<>(((ConstructorDeclaration) callable).getBody().getStatements());
        }
        return null;
    }

    private String normalizeWindow(List<Statement> stmts) {
        StringBuilder sb = new StringBuilder();
        for (Statement s : stmts) {
            sb.append(normalize(s)).append("|");
        }
        return sb.toString();
    }

    private String normalize(Statement s) {
        // 変数名・識別子・リテラルを削除して構造だけ残す
        return s.toString()
                .replaceAll("\\b[a-z][A-Za-z0-9_]*\\b", "V")  // 変数名
                .replaceAll("\"[^\"]*\"", "S")                 // 文字列リテラル
                .replaceAll("\\b\\d+\\b", "N")                 // 数値リテラル
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int startLine(Statement s) {
        return s.getBegin().map(p -> p.line).orElse(0);
    }

    private String truncate(String s, int max) {
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
