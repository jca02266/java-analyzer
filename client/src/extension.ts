import * as fs from 'fs';
import * as path from 'path';
import { execSync } from 'child_process';
import * as vscode from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    State,
} from 'vscode-languageclient/node';
import { MetricsPanel } from './views/metricsPanel';

let client: LanguageClient;

export function activate(context: vscode.ExtensionContext): void {
    try {
    const outputChannel = vscode.window.createOutputChannel('Java Analyzer');
    context.subscriptions.push(outputChannel);

    const serverJar = context.asAbsolutePath(
        path.join('server', 'target', 'java-analyzer-server-1.0-SNAPSHOT.jar')
    );

    // Startup diagnostics written to the Output panel
    outputChannel.appendLine('=== Java Analyzer startup ===');
    outputChannel.appendLine(`Server JAR: ${serverJar}`);

    if (!fs.existsSync(serverJar)) {
        const msg = `Server JAR not found: ${serverJar}`;
        outputChannel.appendLine(`ERROR: ${msg}`);
        outputChannel.show(true);
        vscode.window.showErrorMessage(`Java Analyzer: ${msg}`);
        return;
    }
    outputChannel.appendLine('Server JAR: OK');

    try {
        const javaVerOutput = execSync('java -version 2>&1').toString().trim();
        outputChannel.appendLine(`Java: ${javaVerOutput}`);
        const match = javaVerOutput.match(/version "(\d+)(?:\.(\d+))?/);
        if (match) {
            let major = parseInt(match[1], 10);
            if (major === 1) { major = parseInt(match[2], 10); } // 1.8 -> 8
            if (major < 11) {
                const msg = `Java 11 or later is required (found Java ${major}). lsp4j requires Java 11+. Please install JDK 11+.`;
                outputChannel.appendLine(`ERROR: ${msg}`);
                outputChannel.show(true);
                vscode.window.showErrorMessage(`Java Analyzer: ${msg}`);
                return;
            }
            outputChannel.appendLine(`Java version: ${major} (OK)`);
        }
    } catch {
        const msg = '"java" command not found. Please install JDK 11 or later and add it to PATH.';
        outputChannel.appendLine(`ERROR: ${msg}`);
        outputChannel.show(true);
        vscode.window.showErrorMessage(`Java Analyzer: ${msg}`);
        return;
    }

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
        outputChannel,
    };

    client = new LanguageClient(
        'javaAnalyzer',
        'Java Analyzer',
        serverOptions,
        clientOptions
    );

    const assertRunning = (): boolean => {
        if (client.state !== State.Running) {
            vscode.window.showErrorMessage(
                'Java Analyzer: server is not running. Check the Output panel (Java Analyzer) for details.',
                'Show Output'
            ).then(sel => { if (sel === 'Show Output') { outputChannel.show(true); } });
            return false;
        }
        return true;
    };

    context.subscriptions.push(
        vscode.commands.registerCommand('javaAnalyzer.analyzeFile', async () => {
            if (!assertRunning()) { return; }
            const editor = vscode.window.activeTextEditor;
            if (!editor || editor.document.languageId !== 'java') {
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
            if (!assertRunning()) { return; }
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
            if (!assertRunning()) { return; }
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
