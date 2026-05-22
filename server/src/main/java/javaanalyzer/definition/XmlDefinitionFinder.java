package javaanalyzer.definition;

import org.eclipse.lsp4j.Location;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBatis XML ファイルのカーソル位置から Java @Mapper メソッド定義へジャンプする。
 * カーソルが id="xxx" の値の上にあるとき、WorkspaceIndex を使って Java 側の行を返す。
 */
public class XmlDefinitionFinder {

    private static final Pattern ID_PATTERN        = Pattern.compile("\\bid=\"([^\"]+)\"");
    private static final Pattern NAMESPACE_PATTERN  = Pattern.compile("namespace=\"([^\"]+)\"");

    private final WorkspaceIndex index;

    public XmlDefinitionFinder(WorkspaceIndex index) {
        this.index = index;
    }

    public Optional<Location> find(String fileUri, int lspLine, int lspChar) {
        String filePath = uriToPath(fileUri);
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Optional.empty();
        }

        // namespace を取得（ファイル全体をスキャン）
        String namespace = findNamespace(lines);
        if (namespace == null) return Optional.empty();

        // カーソル行から id 値を取得
        if (lspLine >= lines.size()) return Optional.empty();
        String cursorLine = lines.get(lspLine);
        String id = findIdAtCursor(cursorLine, lspChar);
        if (id == null) return Optional.empty();

        // "namespace#id" で検索
        return Optional.ofNullable(index.getMapperForXml(namespace + "#" + id));
    }

    /** ファイル先頭付近の namespace="..." を取得 */
    private String findNamespace(List<String> lines) {
        for (String line : lines) {
            Matcher m = NAMESPACE_PATTERN.matcher(line);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    /** カーソル位置（列）が id="xxx" の値の範囲内にあれば id 文字列を返す */
    private String findIdAtCursor(String line, int lspChar) {
        Matcher m = ID_PATTERN.matcher(line);
        while (m.find()) {
            // id 値の開始・終了位置（" の内側）
            int valueStart = m.start(1);
            int valueEnd   = m.end(1);
            // 属性全体（id="..."）の範囲でもカーソルを受け付ける
            if (lspChar >= m.start() && lspChar <= m.end()) {
                return m.group(1);
            }
        }
        return null;
    }

    private String uriToPath(String uri) {
        try {
            return Paths.get(URI.create(uri)).toString();
        } catch (Exception e) {
            return uri.replaceFirst("^file://", "");
        }
    }
}
