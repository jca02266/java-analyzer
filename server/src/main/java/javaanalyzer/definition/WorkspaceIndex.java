package javaanalyzer.definition;

import javaanalyzer.mybatis.SqlStatementInfo;
import javaanalyzer.mybatis.XmlMapperReport;
import javaanalyzer.spring.MapperInterfaceInfo;
import javaanalyzer.spring.SpringReport;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ワークスペース解析後に保持する双方向インデックス。
 * WorkspaceService が populate し、TextDocumentService が参照する。
 */
public class WorkspaceIndex {

    private static final WorkspaceIndex INSTANCE = new WorkspaceIndex();

    public static WorkspaceIndex getInstance() { return INSTANCE; }

    /** "simpleClassName#methodName" → XML の Location */
    private final Map<String, Location> mapperToXml = new ConcurrentHashMap<>();

    /** "namespace#statementId" → Java @Mapper メソッドの Location */
    private final Map<String, Location> xmlToMapper = new ConcurrentHashMap<>();

    private volatile String workspacePath;
    private volatile String languageLevel = "JAVA_8";

    public void clear() {
        mapperToXml.clear();
        xmlToMapper.clear();
    }

    public void setWorkspace(String path, String level) {
        this.workspacePath = path;
        this.languageLevel = level;
    }

    public String getWorkspacePath() { return workspacePath; }
    public String getLanguageLevel() { return languageLevel; }

    // ------------------------------------------------------------------
    // インデックス構築
    // ------------------------------------------------------------------

    public void build(SpringReport spring, List<XmlMapperReport> xmlMappers) {
        clear();
        if (spring == null || xmlMappers == null) return;

        // MapperInterfaceInfo をクラス名でインデックス化
        Map<String, MapperInterfaceInfo> mapperByClass = new ConcurrentHashMap<>();
        Map<String, MapperInterfaceInfo> mapperByFqn   = new ConcurrentHashMap<>();
        if (spring.mapperInterfaces != null) {
            for (MapperInterfaceInfo mi : spring.mapperInterfaces) {
                mapperByClass.put(mi.className, mi);
            }
        }

        for (XmlMapperReport xm : xmlMappers) {
            if (xm.statements == null) continue;
            String ns = xm.namespace; // 例: com.example.demo.mapper.UserMapper

            // namespace の末尾がクラス名
            String simpleClass = ns.contains(".")
                    ? ns.substring(ns.lastIndexOf('.') + 1)
                    : ns;
            MapperInterfaceInfo mi = mapperByClass.get(simpleClass);

            for (SqlStatementInfo stmt : xm.statements) {
                // XML → Java
                String xmlKey = ns + "#" + stmt.id;
                if (mi != null && mi.filePath != null && mi.methodLines != null) {
                    Integer javaLine = mi.methodLines.get(stmt.id);
                    if (javaLine != null) {
                        xmlToMapper.put(xmlKey, makeLocation(mi.filePath, javaLine - 1));
                    }
                }

                // Java → XML
                if (stmt.line > 0) {
                    String javaKey = simpleClass + "#" + stmt.id;
                    mapperToXml.put(javaKey, makeLocation(xm.filePath, stmt.line - 1));
                    // 完全修飾名でも登録
                    mapperToXml.put(ns + "#" + stmt.id, makeLocation(xm.filePath, stmt.line - 1));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 検索
    // ------------------------------------------------------------------

    /** Java @Mapper メソッド → XML Location。キー: "ClassName#method" または "fqn#method" */
    public Location getXmlForMapper(String key) {
        return mapperToXml.get(key);
    }

    /** XML statement id → Java @Mapper Location。キー: "namespace#id" */
    public Location getMapperForXml(String key) {
        return xmlToMapper.get(key);
    }

    // ------------------------------------------------------------------

    private static Location makeLocation(String filePath, int line0) {
        String uri = filePath.startsWith("file:") ? filePath : "file://" + filePath;
        Position pos = new Position(line0, 0);
        return new Location(uri, new Range(pos, pos));
    }
}
