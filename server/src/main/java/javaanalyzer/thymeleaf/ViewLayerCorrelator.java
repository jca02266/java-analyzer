package javaanalyzer.thymeleaf;

import javaanalyzer.spring.EndpointInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Correlates view layer (Thymeleaf, HTML, JavaScript) with Spring Controller endpoints.
 * Matches extracted links and AJAX URLs to EndpointInfo.path by converting path parameters.
 */
public class ViewLayerCorrelator {

    public ThymeleafReport correlate(ThymeleafReport viewReport,
                                      List<JsAjaxCallInfo> ajaxCalls,
                                      List<EndpointInfo> endpoints) {
        viewReport.ajaxCalls = ajaxCalls;
        viewReport.unresolvedAjaxCalls = new ArrayList<>();

        // Resolve Thymeleaf links
        List<ThymeleafLinkInfo> links = viewReport.links != null ? viewReport.links : new ArrayList<>();
        for (ThymeleafLinkInfo link : links) {
            link.resolvedEndpoint = findMatchingEndpoint(link.href, "GET", endpoints);
            if (link.resolvedEndpoint == null && "th:action".equals(link.linkType)) {
                link.resolvedEndpoint = findMatchingEndpoint(link.href, "POST", endpoints);
            }
            if (link.resolvedEndpoint == null) {
                viewReport.unresolvedLinks.add(link.templatePath + ":" + link.line + " → " + link.href);
            }
        }

        // Resolve AJAX calls
        for (JsAjaxCallInfo call : ajaxCalls) {
            call.resolvedEndpoint = findMatchingEndpoint(call.url, call.method, endpoints);
            if (call.resolvedEndpoint == null) {
                viewReport.unresolvedAjaxCalls.add(call.sourcePath + ":" + call.line + " → " + call.method + " " + call.url);
            }
        }

        return viewReport;
    }

    /**
     * Matches a URL path to an EndpointInfo by converting Spring path parameters.
     * Spring path: /user/{id} → Regex: /user/[^/]+
     * Thymeleaf URL: /user/123 → Matches
     */
    private String findMatchingEndpoint(String urlPath, String httpMethod, List<EndpointInfo> endpoints) {
        if (urlPath == null || endpoints == null || endpoints.isEmpty()) {
            return null;
        }

        // Normalize URL: remove query string and fragment
        String normalizedUrl = urlPath.split("[?#]")[0];

        for (EndpointInfo endpoint : endpoints) {
            if (endpoint.path == null || !endpoint.httpMethod.equalsIgnoreCase(httpMethod)) {
                continue;
            }

            // Exact match first
            if (normalizedUrl.equals(endpoint.path)) {
                return endpoint.handlerClass + "#" + endpoint.handlerMethod;
            }

            // Try regex match (convert {id} to [^/]+)
            String endpointRegex = "^" + convertPathToRegex(endpoint.path) + "$";
            if (normalizedUrl.matches(endpointRegex)) {
                return endpoint.handlerClass + "#" + endpoint.handlerMethod;
            }
        }

        return null;
    }

    /**
     * Converts Spring path pattern to regex.
     * /user/{id}/orders/{orderId} → /user/[^/]+/orders/[^/]+
     */
    private String convertPathToRegex(String path) {
        return path.replaceAll("\\{[^}]+\\}", "[^/]+");
    }
}
