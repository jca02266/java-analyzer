package javaanalyzer.mybatis;

import org.xml.sax.InputSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * MyBatis XML マッパーファイルを解析して SQL 複雑度を計測する。
 * DTD バリデーションは無効化してオフライン環境でも動作する。
 */
public class MyBatisXmlParser {

    private static final List<String> STMT_TYPES = Arrays.asList("select", "insert", "update", "delete");
    private static final Pattern JOIN_PATTERN     = Pattern.compile("\\bJOIN\\b",    Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_PATTERN   = Pattern.compile("\\bSELECT\\b",  Pattern.CASE_INSENSITIVE);
    private static final Pattern UNION_PATTERN    = Pattern.compile("\\bUNION\\b",   Pattern.CASE_INSENSITIVE);

    public List<XmlMapperReport> parseWorkspace(String workspacePath, List<String> warnings) {
        List<XmlMapperReport> reports = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(Paths.get(workspacePath))) {
            stream.filter(p -> p.toString().endsWith(".xml"))
                  .filter(p -> !isTestPath(p))
                  .forEach(xmlFile -> {
                      XmlMapperReport report = parseFile(xmlFile.toString());
                      if (report != null) reports.add(report);
                  });
        } catch (IOException e) {
            warnings.add("XML walk error: " + e.getMessage());
        }
        return reports;
    }

    public XmlMapperReport parseFile(String filePath) {
        try {
            Document doc = buildDocument(filePath);
            Element root = doc.getDocumentElement();
            if (!"mapper".equalsIgnoreCase(root.getTagName())) return null;

            XmlMapperReport report = new XmlMapperReport();
            report.filePath   = filePath;
            report.namespace  = root.getAttribute("namespace");
            report.statements = new ArrayList<>();

            Map<String, Integer> lineIndex = buildLineIndex(filePath);
            for (String stmtType : STMT_TYPES) {
                NodeList nodes = root.getElementsByTagName(stmtType);
                for (int i = 0; i < nodes.getLength(); i++) {
                    SqlStatementInfo s = parseStatement((Element) nodes.item(i), stmtType);
                    s.line = lineIndex.getOrDefault(s.id, 0);
                    report.statements.add(s);
                }
            }

            return report;
        } catch (Exception e) {
            return null;
        }
    }

    private SqlStatementInfo parseStatement(Element elem, String type) {
        SqlStatementInfo s = new SqlStatementInfo();
        s.id            = elem.getAttribute("id");
        s.statementType = type;
        s.resultType    = elem.getAttribute("resultType");
        s.parameterType = elem.getAttribute("parameterType");

        String sql = elem.getTextContent();

        // SQL キーワード計測
        s.joinCount     = countMatches(sql, JOIN_PATTERN);
        int selectCount = countMatches(sql, SELECT_PATTERN);
        s.subqueryCount = Math.max(0, selectCount - 1);
        s.unionCount    = countMatches(sql, UNION_PATTERN);

        // 動的 SQL タグ計測
        s.ifTagCount      = elem.getElementsByTagName("if").getLength();
        s.foreachTagCount = elem.getElementsByTagName("foreach").getLength();
        s.chooseTagCount  = elem.getElementsByTagName("choose").getLength();
        s.whereTagCount   = elem.getElementsByTagName("where").getLength();
        s.setTagCount     = elem.getElementsByTagName("set").getLength();

        return s;
    }

    private int countMatches(String text, Pattern pattern) {
        long count = pattern.matcher(text).results().count();
        return (int) count;
    }

    private Document buildDocument(String filePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        // DTD / 外部エンティティを無視（オフライン環境対応）
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        builder.setErrorHandler(null);
        return builder.parse(new File(filePath));
    }

    /** XML ファイルを行スキャンして id="xxx" の行番号を収集する（1-based） */
    private Map<String, Integer> buildLineIndex(String filePath) {
        Map<String, Integer> result = new HashMap<>();
        Pattern idPat = Pattern.compile("\\bid=\"([^\"]+)\"");
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = idPat.matcher(lines.get(i));
                if (m.find()) {
                    result.putIfAbsent(m.group(1), i + 1);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return result;
    }

    private boolean isTestPath(Path p) {
        String s = p.toString();
        return s.contains("/test/") || s.contains("\\test\\");
    }
}
