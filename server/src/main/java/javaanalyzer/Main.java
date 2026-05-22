package javaanalyzer;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.concurrent.Future;

public class Main {
    public static void main(String[] args) throws Exception {
        AnalyzerLanguageServer server = new AnalyzerLanguageServer();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
                server, System.in, System.out);
        LanguageClient client = launcher.getRemoteProxy();
        server.connect(client);
        Future<?> listening = launcher.startListening();
        listening.get();
    }
}
