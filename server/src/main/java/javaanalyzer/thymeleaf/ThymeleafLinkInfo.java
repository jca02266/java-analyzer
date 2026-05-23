package javaanalyzer.thymeleaf;

public class ThymeleafLinkInfo {
    public String templatePath;        // Template file path
    public String href;                // Extracted path (e.g., "/user/list")
    public String linkType;            // "th:href" / "th:action" / "th:src" / "data-th-*"
    public int    line;                // Line number in template (1-based)
    public String resolvedEndpoint;    // Matched endpoint (null if unresolved)
}
