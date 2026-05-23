package javaanalyzer.thymeleaf;

public class JsAjaxCallInfo {
    public String sourcePath;          // JS file or template path
    public String url;                 // Request URL (e.g., "/api/users")
    public String method;              // HTTP method: "GET", "POST", "PUT", "DELETE", "PATCH"
    public int    line;                // Line number in source (1-based)
    public String resolvedEndpoint;    // Matched endpoint (null if unresolved)
}
