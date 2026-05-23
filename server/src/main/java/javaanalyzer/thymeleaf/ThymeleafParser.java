package javaanalyzer.thymeleaf;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ThymeleafParser {
    private static final Pattern THYMELEAF_PATH = Pattern.compile("@\\{([^}(]+)");
    private static final Pattern[] THYMELEAF_ATTRS = {
        Pattern.compile("th:href\\s*=\\s*['\"]([^'\"]*)['\"]"),
        Pattern.compile("th:action\\s*=\\s*['\"]([^'\"]*)['\"]"),
        Pattern.compile("th:src\\s*=\\s*['\"]([^'\"]*)['\"]"),
        Pattern.compile("data-th-href\\s*=\\s*['\"]([^'\"]*)['\"]"),
        Pattern.compile("data-th-action\\s*=\\s*['\"]([^'\"]*)['\"]"),
        Pattern.compile("data-th-src\\s*=\\s*['\"]([^'\"]*)['\"]")
    };

    public List<ThymeleafLinkInfo> parseLinksFromFile(String filePath, List<String> warnings) {
        List<ThymeleafLinkInfo> links = new ArrayList<>();
        try {
            String content = Files.readString(Paths.get(filePath));
            Map<Integer, String> lineMap = buildLineMap(content);

            for (int i = 0; i < THYMELEAF_ATTRS.length; i++) {
                Pattern attrPattern = THYMELEAF_ATTRS[i];
                String attrType = getAttributeType(i);
                Matcher matcher = attrPattern.matcher(content);
                while (matcher.find()) {
                    String attrValue = matcher.group(1);
                    Matcher pathMatcher = THYMELEAF_PATH.matcher(attrValue);
                    if (pathMatcher.find()) {
                        String path = pathMatcher.group(1);
                        int lineNum = getLineNumber(content, matcher.start(), lineMap);
                        ThymeleafLinkInfo link = new ThymeleafLinkInfo();
                        link.templatePath = filePath;
                        link.href = path;
                        link.linkType = attrType;
                        link.line = lineNum;
                        links.add(link);
                    }
                }
            }
        } catch (IOException e) {
            warnings.add("Thymeleaf parse error [" + filePath + "]: " + e.getMessage());
        }
        return links;
    }

    public HtmlStructureInfo parseStructureFromFile(String filePath, List<String> warnings) {
        HtmlStructureInfo info = new HtmlStructureInfo();
        info.templatePath = filePath;
        info.maxDomDepth = 0;
        info.formCount = 0;
        info.inlineScriptCount = 0;

        try {
            Document doc = Jsoup.parse(new File(filePath), "UTF-8");
            Element root = doc.selectFirst("html");
            if (root != null) {
                info.maxDomDepth = calculateDomDepth(root);
                info.formCount = doc.select("form").size();
                info.inlineScriptCount = doc.select("script").size();
            }
        } catch (IOException e) {
            // Silently skip malformed HTML
        }
        return info;
    }

    public ThymeleafReport parseWorkspace(String workspacePath, List<String> warnings) {
        ThymeleafReport report = new ThymeleafReport();
        report.links = new ArrayList<>();
        report.structures = new ArrayList<>();
        report.unresolvedLinks = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(Paths.get(workspacePath))) {
            stream.filter(p -> p.toString().endsWith(".html"))
                  .map(Path::toString)
                  .forEach(filePath -> {
                      report.links.addAll(parseLinksFromFile(filePath, warnings));
                      report.structures.add(parseStructureFromFile(filePath, warnings));
                  });
        } catch (IOException e) {
            warnings.add("HTML walk error: " + e.getMessage());
        }

        return report;
    }

    private int calculateDomDepth(Element element) {
        int maxChildDepth = 0;
        for (Element child : element.children()) {
            int childDepth = calculateDomDepth(child);
            maxChildDepth = Math.max(maxChildDepth, childDepth);
        }
        return 1 + maxChildDepth;
    }

    private String getAttributeType(int index) {
        switch (index) {
            case 0: return "th:href";
            case 1: return "th:action";
            case 2: return "th:src";
            case 3: return "data-th-href";
            case 4: return "data-th-action";
            case 5: return "data-th-src";
            default: return "unknown";
        }
    }

    private Map<Integer, String> buildLineMap(String content) {
        Map<Integer, String> lineMap = new HashMap<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lineMap.put(i, lines[i]);
        }
        return lineMap;
    }

    private int getLineNumber(String content, int offset, Map<Integer, String> lineMap) {
        int lineNum = 1;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') lineNum++;
        }
        return lineNum;
    }
}
