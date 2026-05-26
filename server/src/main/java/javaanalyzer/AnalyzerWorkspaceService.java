package javaanalyzer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import javaanalyzer.analyzer.JavaFileAnalyzer;
import javaanalyzer.analyzer.WorkspaceAnalyzer;
import javaanalyzer.definition.WorkspaceIndex;
import javaanalyzer.metrics.FileReport;
import javaanalyzer.metrics.WorkspaceReport;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AnalyzerWorkspaceService implements WorkspaceService {

    private LanguageClient client;

    public void connect(LanguageClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        switch (params.getCommand()) {
            case "javaAnalyzer/ping":
                return CompletableFuture.completedFuture("pong");
            case "javaAnalyzer/analyzeFile":
                return handleAnalyzeFile(params.getArguments());
            case "javaAnalyzer/analyzeWorkspace":
                return handleAnalyzeWorkspace(params.getArguments());
            default:
                return CompletableFuture.completedFuture("unknown command: " + params.getCommand());
        }
    }

    private CompletableFuture<Object> handleAnalyzeFile(List<Object> args) {
        if (args == null || args.isEmpty()) {
            return CompletableFuture.completedFuture("{\"error\":\"no file URI provided\"}");
        }
        try {
            String uriStr = ((JsonPrimitive) args.get(0)).getAsString();
            String filePath = Paths.get(URI.create(uriStr)).toString();

            // TODO: 設定から languageLevel を取得する（Phase4 で対応）
            JavaFileAnalyzer analyzer = new JavaFileAnalyzer("JAVA_8");
            FileReport report = analyzer.analyze(filePath);

            String json = new GsonBuilder().setPrettyPrinting().create().toJson(report);
            return CompletableFuture.completedFuture(json);
        } catch (Exception e) {
            return CompletableFuture.completedFuture("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private CompletableFuture<Object> handleAnalyzeWorkspace(List<Object> args) {
        if (args == null || args.isEmpty()) {
            return CompletableFuture.completedFuture("{\"error\":\"no workspace path provided\"}");
        }
        try {
            String workspacePath = ((JsonPrimitive) args.get(0)).getAsString();
            WorkspaceAnalyzer analyzer = new WorkspaceAnalyzer(workspacePath, "JAVA_8");
            WorkspaceReport report = analyzer.analyze();

            // 定義ジャンプ用インデックスを更新
            WorkspaceIndex idx = WorkspaceIndex.getInstance();
            idx.setWorkspace(workspacePath, "JAVA_8");
            idx.build(report.springReport,
                      report.mybatisReport != null ? report.mybatisReport.xmlMappers : null);

            String json = new GsonBuilder().setPrettyPrinting().create().toJson(report);
            return CompletableFuture.completedFuture(json);
        } catch (Exception e) {
            return CompletableFuture.completedFuture("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /** Called from initialize() to build the definition-jump index in the background. */
    public void buildIndex(String workspacePath) {
        try {
            WorkspaceAnalyzer analyzer = new WorkspaceAnalyzer(workspacePath, "JAVA_8");
            WorkspaceReport report = analyzer.analyze();
            WorkspaceIndex idx = WorkspaceIndex.getInstance();
            idx.setWorkspace(workspacePath, "JAVA_8");
            idx.build(report.springReport,
                      report.mybatisReport != null ? report.mybatisReport.xmlMappers : null);
        } catch (Exception ignored) {
            // Background index build: silently ignore failures
        }
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {}

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {}
}
