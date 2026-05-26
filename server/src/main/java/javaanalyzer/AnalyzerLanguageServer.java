package javaanalyzer;

import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AnalyzerLanguageServer implements LanguageServer, LanguageClientAware {

    private final AnalyzerTextDocumentService textDocumentService;
    private final AnalyzerWorkspaceService workspaceService;

    public AnalyzerLanguageServer() {
        this.workspaceService = new AnalyzerWorkspaceService();
        this.textDocumentService = new AnalyzerTextDocumentService(workspaceService);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setExecuteCommandProvider(new ExecuteCommandOptions(List.of(
                "javaAnalyzer/ping",
                "javaAnalyzer/analyzeFile",
                "javaAnalyzer/analyzeWorkspace"
        )));
        capabilities.setDefinitionProvider(true);

        // Resolve workspace root from workspaceFolders or rootUri
        String workspacePath = null;
        List<WorkspaceFolder> folders = params.getWorkspaceFolders();
        if (folders != null && !folders.isEmpty()) {
            workspacePath = Paths.get(URI.create(folders.get(0).getUri())).toString();
        } else if (params.getRootUri() != null) {
            workspacePath = Paths.get(URI.create(params.getRootUri())).toString();
        }

        if (workspacePath != null) {
            final String path = workspacePath;
            // Build the definition-jump index in the background so F12 works
            // without requiring a manual Analyze Workspace command
            CompletableFuture.runAsync(() -> workspaceService.buildIndex(path));
        }

        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        workspaceService.connect(client);
    }
}
