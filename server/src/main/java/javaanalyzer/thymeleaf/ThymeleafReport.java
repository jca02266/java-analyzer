package javaanalyzer.thymeleaf;

import java.util.List;

public class ThymeleafReport {
    public List<ThymeleafLinkInfo> links;             // Extracted Thymeleaf links
    public List<HtmlStructureInfo> structures;        // HTML structure metrics
    public List<JsAjaxCallInfo>    ajaxCalls;         // Detected AJAX calls
    public List<String>            unresolvedLinks;   // Links that didn't match endpoints
    public List<String>            unresolvedAjaxCalls; // AJAX calls that didn't match endpoints
}
