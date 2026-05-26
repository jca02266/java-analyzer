package javaanalyzer.definition;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserVariableDeclaration;
import javaanalyzer.analyzer.JavaFileAnalyzer;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Java ファイルの定義ジャンプを解決する。
 *
 * カーソル位置に応じて以下を返す：
 * - @Mapper インターフェースのメソッド宣言上  → [XML 定義]
 * - @Mapper メソッドの呼び出し元             → [Java 宣言, XML 定義]  ← redhat.java 互換
 * - 通常のメソッド呼び出し / 変数 / 型参照   → [Java 宣言]
 */
public class DefinitionFinder {

    private final WorkspaceIndex index;

    public DefinitionFinder(WorkspaceIndex index) {
        this.index = index;
    }

    /** カーソル位置に対応する定義 Location のリストを返す（0件・1件・2件）。 */
    public List<Location> find(String fileUri, int lspLine, int lspChar) {
        String filePath = uriToPath(fileUri);

        JavaFileAnalyzer analyzer = new JavaFileAnalyzer(
                index.getLanguageLevel(), index.getWorkspacePath());

        CompilationUnit cu;
        try {
            cu = analyzer.createParser().parse(new File(filePath))
                    .getResult().orElse(null);
        } catch (Exception e) {
            return Collections.emptyList();
        }
        if (cu == null) return Collections.emptyList();

        // JavaParser は 1-based
        int jpLine = lspLine + 1;
        int jpCol  = lspChar + 1;

        // ① カーソルが @Mapper インターフェースのメソッド宣言上にある
        //   → XML 定義のみ返す（Java 宣言はカーソル位置そのもの）
        List<Location> mapperDeclResult = findXmlForMapperDeclaration(cu, jpLine, jpCol);
        if (!mapperDeclResult.isEmpty()) return mapperDeclResult;

        // ② メソッド呼び出しを解決
        //   @Mapper メソッドなら [Java 宣言, XML 定義]、それ以外は [Java 宣言]
        Optional<MethodCallExpr> mc = findNodeAt(cu, MethodCallExpr.class, jpLine, jpCol);
        if (mc.isPresent()) {
            List<Location> mcResult = resolveMethodCall(mc.get());
            if (!mcResult.isEmpty()) return mcResult;
        }

        // ③ 変数・フィールド参照
        Optional<NameExpr> nameExpr = findNodeAt(cu, NameExpr.class, jpLine, jpCol);
        if (nameExpr.isPresent()) {
            List<Location> nameResult = resolveNameExpr(nameExpr.get());
            if (!nameResult.isEmpty()) return nameResult;
        }

        // ④ 型参照
        return findNodeAt(cu, ClassOrInterfaceType.class, jpLine, jpCol)
                .map(t -> resolveTypeRef(t))
                .orElse(Collections.emptyList());
    }

    // ------------------------------------------------------------------
    // ① @Mapper インターフェースのメソッド宣言 → XML
    // ------------------------------------------------------------------

    private List<Location> findXmlForMapperDeclaration(CompilationUnit cu, int jpLine, int jpCol) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> containsPosition(m, jpLine, jpCol))
                .filter(this::isInMapperInterface)
                .findFirst()
                .map(m -> {
                    String methodName  = m.getNameAsString();
                    String simpleClass = m.findAncestor(ClassOrInterfaceDeclaration.class)
                            .map(ClassOrInterfaceDeclaration::getNameAsString).orElse("");
                    String pkg = cu.getPackageDeclaration()
                            .map(pd -> pd.getNameAsString() + ".").orElse("");

                    Location xml = index.getXmlForMapper(simpleClass + "#" + methodName);
                    if (xml == null) xml = index.getXmlForMapper(pkg + simpleClass + "#" + methodName);
                    return xml != null ? Collections.singletonList(xml) : Collections.<Location>emptyList();
                })
                .orElse(Collections.emptyList());
    }

    // ------------------------------------------------------------------
    // ② メソッド呼び出しを解決
    //    @Mapper メソッド → [Java 宣言, XML 定義]
    //    それ以外         → [Java 宣言]
    // ------------------------------------------------------------------

    private List<Location> resolveMethodCall(MethodCallExpr mc) {
        try {
            ResolvedMethodDeclaration resolved = mc.resolve();
            if (!(resolved instanceof JavaParserMethodDeclaration)) return Collections.emptyList();

            MethodDeclaration decl = ((JavaParserMethodDeclaration) resolved).getWrappedNode();
            List<Location> results = new ArrayList<>();
            nodeToLocation(decl).ifPresent(results::add);

            // 解決先が @Mapper インターフェースのメソッドなら XML も追加
            if (isInMapperInterface(decl)) {
                String methodName  = decl.getNameAsString();
                String simpleClass = decl.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(ClassOrInterfaceDeclaration::getNameAsString).orElse("");
                String pkg = decl.findCompilationUnit()
                        .flatMap(cu -> cu.getPackageDeclaration())
                        .map(pd -> pd.getNameAsString() + ".").orElse("");

                Location xml = index.getXmlForMapper(simpleClass + "#" + methodName);
                if (xml == null) xml = index.getXmlForMapper(pkg + simpleClass + "#" + methodName);
                if (xml != null) results.add(xml);
            }

            return results;
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    // ------------------------------------------------------------------
    // ③ 変数・フィールド参照
    // ------------------------------------------------------------------

    private List<Location> resolveNameExpr(NameExpr n) {
        try {
            ResolvedValueDeclaration resolved = n.resolve();
            if (resolved instanceof JavaParserFieldDeclaration) {
                return nodeToLocation(((JavaParserFieldDeclaration) resolved).getWrappedNode())
                        .map(Collections::singletonList).orElse(Collections.emptyList());
            }
            if (resolved instanceof JavaParserVariableDeclaration) {
                return nodeToLocation(((JavaParserVariableDeclaration) resolved).getWrappedNode())
                        .map(Collections::singletonList).orElse(Collections.emptyList());
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    // ------------------------------------------------------------------
    // ④ 型参照
    // ------------------------------------------------------------------

    private List<Location> resolveTypeRef(ClassOrInterfaceType t) {
        try {
            com.github.javaparser.resolution.types.ResolvedType raw = t.resolve();
            if (!raw.isReferenceType()) return Collections.emptyList();
            ResolvedReferenceType resolved = raw.asReferenceType();
            com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration decl = resolved.getTypeDeclaration().orElse(null);
            if (decl instanceof com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration) {
                Node wrapped = ((com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration) decl).getWrappedNode();
                return nodeToLocation(wrapped).map(Collections::singletonList).orElse(Collections.emptyList());
            }
            if (decl instanceof com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserInterfaceDeclaration) {
                Node wrapped = ((com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserInterfaceDeclaration) decl).getWrappedNode();
                return nodeToLocation(wrapped).map(Collections::singletonList).orElse(Collections.emptyList());
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    // ------------------------------------------------------------------
    // ユーティリティ
    // ------------------------------------------------------------------

    private boolean isInMapperInterface(MethodDeclaration m) {
        return m.findAncestor(ClassOrInterfaceDeclaration.class)
                .filter(ClassOrInterfaceDeclaration::isInterface)
                .filter(c -> c.getAnnotations().stream()
                        .anyMatch(a -> "Mapper".equals(a.getNameAsString())))
                .isPresent();
    }

    private Optional<Location> nodeToLocation(Node node) {
        return node.getBegin().flatMap(begin ->
            node.findCompilationUnit().flatMap(cu ->
                cu.getStorage().map(s -> {
                    String path = s.getPath().toAbsolutePath().toString();
                    int line0 = begin.line - 1;
                    Position pos = new Position(line0, 0);
                    return new Location("file://" + path, new Range(pos, pos));
                })
            )
        );
    }

    private <T extends Node> Optional<T> findNodeAt(CompilationUnit cu, Class<T> cls, int jpLine, int jpCol) {
        return cu.findAll(cls).stream()
                .filter(n -> containsPosition(n, jpLine, jpCol))
                .min(Comparator.comparingInt(this::rangeSize));
    }

    private boolean containsPosition(Node n, int line, int col) {
        return n.getBegin().flatMap(begin ->
            n.getEnd().map(end ->
                (begin.line < line || (begin.line == line && begin.column <= col)) &&
                (end.line > line   || (end.line   == line && end.column   >= col))
            )
        ).orElse(false);
    }

    private int rangeSize(Node n) {
        return n.getBegin().flatMap(b -> n.getEnd().map(e ->
            (e.line - b.line) * 10000 + (e.column - b.column)
        )).orElse(Integer.MAX_VALUE);
    }

    private String uriToPath(String uri) {
        try {
            return Paths.get(URI.create(uri)).toString();
        } catch (Exception e) {
            return uri.replaceFirst("^file://", "");
        }
    }
}
