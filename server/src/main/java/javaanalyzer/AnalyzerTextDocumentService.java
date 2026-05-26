package javaanalyzer;

import com.github.javaparser.ast.CompilationUnit;
import javaanalyzer.analyzer.JavaFileAnalyzer;
import javaanalyzer.analyzer.ParsedFileCache;
import javaanalyzer.definition.DefinitionFinder;
import javaanalyzer.definition.WorkspaceIndex;
import javaanalyzer.definition.XmlDefinitionFinder;
import javaanalyzer.diagnostics.EqualsMismatchDetector;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class AnalyzerTextDocumentService implements TextDocumentService {

    private final WorkspaceIndex index = WorkspaceIndex.getInstance();
    private final AnalyzerWorkspaceService workspaceService;
    // Holds the running re-index future so a new save can cancel the previous one
    private final AtomicReference<CompletableFuture<?>> pendingReindex = new AtomicReference<>();

    public AnalyzerTextDocumentService(AnalyzerWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
            DefinitionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri    = params.getTextDocument().getUri();
            int line      = params.getPosition().getLine();
            int character = params.getPosition().getCharacter();

            List<Location> result;
            if (isXmlFile(uri)) {
                // XML → Java: 常に 0 or 1 件
                result = new XmlDefinitionFinder(index).find(uri, line, character)
                        .map(Collections::singletonList)
                        .orElse(Collections.emptyList());
            } else {
                // Java → Java / Java → [Java + XML]: 0〜2 件
                result = new DefinitionFinder(index).find(uri, line, character);
            }

            return Either.<List<? extends Location>, List<? extends LocationLink>>forLeft(result);
        });
    }

    private boolean isXmlFile(String uri) {
        return uri != null && uri.toLowerCase().endsWith(".xml");
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        if (uri.endsWith(".java")) {
            runDiagnostics(uri);
        }
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {}

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        // Clear diagnostics when file is closed
        workspaceService.publishDiagnostics(
                params.getTextDocument().getUri(), Collections.emptyList());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        if (!uri.endsWith(".java") && !uri.endsWith(".xml")) return;

        String workspacePath = index.getWorkspacePath();
        if (workspacePath == null) return;

        // Cancel previous pending re-index and start a new one
        CompletableFuture<?> prev = pendingReindex.getAndSet(null);
        if (prev != null) prev.cancel(false);

        CompletableFuture<?> next = CompletableFuture.runAsync(
                () -> workspaceService.buildIndex(workspacePath));
        pendingReindex.set(next);

        if (uri.endsWith(".java")) {
            runDiagnostics(uri);
        }
    }

    private void runDiagnostics(String uri) {
        try {
            String filePath = uriToPath(uri);
            String langLevel = index.getLanguageLevel() != null ? index.getLanguageLevel() : "JAVA_11";
            JavaFileAnalyzer analyzer = new JavaFileAnalyzer(langLevel, index.getWorkspacePath());
            CompilationUnit cu = ParsedFileCache.getInstance().get(filePath, analyzer.createParser());
            List<Diagnostic> diagnostics = cu != null
                    ? new EqualsMismatchDetector().detect(cu)
                    : Collections.emptyList();
            workspaceService.publishDiagnostics(uri, diagnostics);
        } catch (Exception ignored) {}
    }

    private String uriToPath(String uri) {
        try {
            return Paths.get(URI.create(uri)).toString();
        } catch (Exception e) {
            return uri.replaceFirst("^file://", "");
        }
    }
}
