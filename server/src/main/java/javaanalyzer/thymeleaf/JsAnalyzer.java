package javaanalyzer.thymeleaf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class JsAnalyzer {
    // Regex patterns for AJAX call detection
    private static final Pattern FETCH_PATTERN = Pattern.compile("fetch\\s*\\(\\s*['\"`]([^'\"`;#\\s]+)");
    private static final Pattern AJAX_URL_PATTERN = Pattern.compile("url\\s*:\\s*['\"`]([^'\"`;#\\s]+)");
    private static final Pattern AXIOS_PATTERN = Pattern.compile("axios\\.(get|post|put|delete|patch|request)\\s*\\(\\s*['\"`]([^'\"`;#\\s]+)");
    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile("<script[^>]*>([\\s\\S]*?)</script>");

    public List<JsAjaxCallInfo> analyzeWorkspace(String workspacePath, List<String> warnings) {
        List<JsAjaxCallInfo> calls = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(Paths.get(workspacePath))) {
            stream.filter(p -> {
                String path = p.toString();
                return path.endsWith(".js") || path.endsWith(".html");
            })
            .forEach(path -> {
                if (path.toString().endsWith(".js")) {
                    calls.addAll(analyzeJsFile(path.toString(), warnings));
                } else if (path.toString().endsWith(".html")) {
                    calls.addAll(analyzeHtmlFile(path.toString(), warnings));
                }
            });
        } catch (IOException e) {
            warnings.add("JavaScript walk error: " + e.getMessage());
        }

        return calls;
    }

    private List<JsAjaxCallInfo> analyzeJsFile(String filePath, List<String> warnings) {
        List<JsAjaxCallInfo> calls = new ArrayList<>();
        try {
            String content = Files.readString(Paths.get(filePath));
            calls.addAll(extractAjaxCalls(filePath, content, warnings));
        } catch (IOException e) {
            warnings.add("JS parse error [" + filePath + "]: " + e.getMessage());
        }
        return calls;
    }

    private List<JsAjaxCallInfo> analyzeHtmlFile(String filePath, List<String> warnings) {
        List<JsAjaxCallInfo> calls = new ArrayList<>();
        try {
            String content = Files.readString(Paths.get(filePath));
            Matcher matcher = SCRIPT_TAG_PATTERN.matcher(content);
            while (matcher.find()) {
                String scriptContent = matcher.group(1);
                calls.addAll(extractAjaxCalls(filePath, scriptContent, warnings));
            }
        } catch (IOException e) {
            warnings.add("HTML parse error [" + filePath + "]: " + e.getMessage());
        }
        return calls;
    }

    private List<JsAjaxCallInfo> extractAjaxCalls(String sourcePath, String content, List<String> warnings) {
        List<JsAjaxCallInfo> calls = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        // Extract fetch() calls
        Matcher fetchMatcher = FETCH_PATTERN.matcher(content);
        while (fetchMatcher.find()) {
            JsAjaxCallInfo call = new JsAjaxCallInfo();
            call.sourcePath = sourcePath;
            call.url = fetchMatcher.group(1);
            call.method = "GET";
            call.line = getLineNumber(content, fetchMatcher.start());
            calls.add(call);
        }

        // Extract $.ajax({url: '...'}) calls
        Matcher ajaxMatcher = AJAX_URL_PATTERN.matcher(content);
        while (ajaxMatcher.find()) {
            JsAjaxCallInfo call = new JsAjaxCallInfo();
            call.sourcePath = sourcePath;
            call.url = ajaxMatcher.group(1);
            // Try to detect method from nearby context
            String context = getContextBefore(content, ajaxMatcher.start(), 200);
            call.method = extractHttpMethod(context);
            call.line = getLineNumber(content, ajaxMatcher.start());
            calls.add(call);
        }

        // Extract axios calls
        Matcher axinosMatcher = AXIOS_PATTERN.matcher(content);
        while (axinosMatcher.find()) {
            JsAjaxCallInfo call = new JsAjaxCallInfo();
            call.sourcePath = sourcePath;
            call.method = axinosMatcher.group(1).toUpperCase();
            call.url = axinosMatcher.group(2);
            call.line = getLineNumber(content, axinosMatcher.start());
            calls.add(call);
        }

        return calls;
    }

    private String extractHttpMethod(String context) {
        if (context.contains("type") && context.contains("GET")) return "GET";
        if (context.contains("type") && context.contains("POST")) return "POST";
        if (context.contains("type") && context.contains("PUT")) return "PUT";
        if (context.contains("type") && context.contains("DELETE")) return "DELETE";
        return "POST"; // Default for $.ajax
    }

    private String getContextBefore(String content, int offset, int length) {
        int start = Math.max(0, offset - length);
        return content.substring(start, offset);
    }

    private int getLineNumber(String content, int offset) {
        int lineNum = 1;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') lineNum++;
        }
        return lineNum;
    }
}
