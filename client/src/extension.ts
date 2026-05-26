import * as path from 'path';
import * as vscode from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
} from 'vscode-languageclient/node';
import { MetricsPanel } from './views/metricsPanel';

let client: LanguageClient;

export function activate(context: vscode.ExtensionContext): void {
    try {
    const serverJar = context.asAbsolutePath(
        path.join('server', 'target', 'java-analyzer-server-1.0-SNAPSHOT.jar')
    );

    const serverOptions: ServerOptions = {
        command: 'java',
        args: ['-jar', serverJar],
    };

    // Detect redhat.java and determine whether to enable Java definition jump
    const redhatInstalled = !!vscode.extensions.getExtension('redhat.java');
    const cfg = vscode.workspace.getConfiguration('javaAnalyzer');
    const javaDefMode = cfg.get<string>('javaDefinition', 'auto');
    const provideJavaDef =
        javaDefMode === 'enabled' ||
        (javaDefMode === 'auto' && !redhatInstalled);

    const documentSelector: LanguageClientOptions['documentSelector'] = [
        // XML mapper files (no conflict with redhat.java, always included)
        { scheme: 'file', pattern: '**/mapper/**/*.xml' },
        { scheme: 'file', pattern: '**/mappers/**/*.xml' },
    ];
    if (provideJavaDef) {
        documentSelector.push({ scheme: 'file', language: 'java' });
    }

    const clientOptions: LanguageClientOptions = {
        documentSelector,
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.{java,xml}'),
        },
    };

    client = new LanguageClient(
        'javaAnalyzer',
        'Java Analyzer',
        serverOptions,
        clientOptions
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('javaAnalyzer.analyzeFile', async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor) {
                vscode.window.showWarningMessage('Please open a Java file.');
                return;
            }
            const json = await client.sendRequest('workspace/executeCommand', {
                command: 'javaAnalyzer/analyzeFile',
                arguments: [editor.document.uri.toString()],
            }) as string;
            try {
                const report = JSON.parse(json);
                if (report.error) {
                    vscode.window.showErrorMessage(`Java Analyzer: ${report.error}`);
                } else {
                    MetricsPanel.show(report);
                }
            } catch {
                vscode.window.showErrorMessage(`Java Analyzer: Failed to parse analysis results`);
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('javaAnalyzer.analyzeWorkspace', async () => {
            const root = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? '';
            if (!root) {
                vscode.window.showWarningMessage('Please open a workspace.');
                return;
            }
            await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Java Analyzer: Analyzing...' },
                async () => {
                    const json = await client.sendRequest('workspace/executeCommand', {
                        command: 'javaAnalyzer/analyzeWorkspace',
                        arguments: [root],
                    }) as string;
                    try {
                        const report = JSON.parse(json);
                        if (report.error) {
                            vscode.window.showErrorMessage(`Java Analyzer: ${report.error}`);
                        } else {
                            MetricsPanel.showWorkspace(report);
                        }
                    } catch {
                        vscode.window.showErrorMessage('Java Analyzer: Failed to parse analysis results');
                    }
                }
            );
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('javaAnalyzer.ping', async () => {
            const result = await client.sendRequest('workspace/executeCommand', {
                command: 'javaAnalyzer/ping',
                arguments: [],
            });
            vscode.window.showInformationMessage(`ping → ${String(result)}`);
        })
    );

    client.start().catch((err: unknown) => {
        const msg = err instanceof Error ? err.message : String(err);
        vscode.window.showErrorMessage(`Java Analyzer: server failed - ${msg}`);
    });
    } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        vscode.window.showErrorMessage(`Java Analyzer activation error: ${msg}`);
        throw err;
    }
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) { return undefined; }
    return client.stop();
}
