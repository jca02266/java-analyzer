import * as vscode from 'vscode';

interface ParameterInfo { name: string; type: string; }
interface MethodMetrics {
    name: string; kind: string; scope: string;
    startLine: number; endLine: number; lineCount: number;
    maxNestDepth: number; localDeclCount: number;
    lambdaCount: number; maxMethodChainDepth: number;
    methodReferenceCount: number;
    referenceCount: number;
    optionalDirectGet: number;
    externalCallCount: number;
    externalCallCategories: string[];
    ioSideEffectCount: number;
    repeatedNumericLiterals: string[];
    assignmentBlocks: { startLine: number; endLine: number; count: number; shape: string }[];
    returnType: string | null;
    parameters: ParameterInfo[];
}
interface PrefixCluster { prefix: string; identifiers: string[]; className: string; }
interface ClassReport {
    className: string; kind: string; scope: string;
    startLine: number; endLine: number;
    methods: MethodMetrics[];
    prefixClusters: PrefixCluster[];
}
interface FileReport {
    filePath: string; totalLines: number;
    classes: ClassReport[];
    warnings: string[];
}
interface EndpointInfo { httpMethod: string; path: string; handlerClass: string; handlerMethod: string; line: number; }
interface BeanInfo     { className: string; beanType: string; }
interface DiEdge       { from: string; to: string; fieldName: string; injectionType: string; }
interface SpringReport {
    endpoints: EndpointInfo[];
    beans: BeanInfo[];
    diGraph: DiEdge[];
    transactionalMethods: string[];
}
interface SqlStatementInfo {
    id: string; statementType: string; resultType: string; parameterType: string;
    joinCount: number; subqueryCount: number; unionCount: number;
    ifTagCount: number; foreachTagCount: number; chooseTagCount: number;
    whereTagCount: number; setTagCount: number;
}
interface XmlMapperReport { filePath: string; namespace: string; statements: SqlStatementInfo[]; }
interface MyBatisReport   { xmlMappers: XmlMapperReport[]; unmappedMethods: string[]; }
interface CallEdge       { from: string; to: string; resolved: boolean; }
interface DuplicateBlock { stmtCount: number; preview: string; locations: string[]; }
interface WorkspaceReport {
    files: FileReport[];
    springReport: SpringReport;
    mybatisReport: MyBatisReport;
    warnings: string[];
    callGraph: CallEdge[];
    deadCodeCandidates: string[];
    entryPointCandidates: string[];
    duplicateBlocks: DuplicateBlock[];
}

export class MetricsPanel {
    private static current: MetricsPanel | undefined;
    private readonly panel: vscode.WebviewPanel;

    static showFile(report: FileReport): void {
        MetricsPanel.open('Java Analyzer — File', buildFileHtml(report));
    }

    static showWorkspace(report: WorkspaceReport): void {
        MetricsPanel.open('Java Analyzer — Workspace', buildWorkspaceHtml(report));
    }

    // 後方互換
    static show(report: FileReport): void { MetricsPanel.showFile(report); }

    private static open(title: string, html: string): void {
        if (MetricsPanel.current) {
            MetricsPanel.current.panel.title = title;
            MetricsPanel.current.panel.webview.html = html;
            MetricsPanel.current.panel.reveal(vscode.ViewColumn.Beside);
            return;
        }
        const panel = vscode.window.createWebviewPanel(
            'javaAnalyzerMetrics', title,
            vscode.ViewColumn.Beside, { enableScripts: false }
        );
        MetricsPanel.current = new MetricsPanel(panel);
        panel.webview.html = html;
        panel.onDidDispose(() => { MetricsPanel.current = undefined; });
    }

    private constructor(panel: vscode.WebviewPanel) {
        this.panel = panel;
    }
}

function buildFileHtml(r: FileReport): string {
    return wrapHtml(
        `<h2>Java Analyzer — File</h2>
         <p><b>${r.filePath}</b> — 総行数: ${r.totalLines} / クラス数: ${r.classes?.length ?? 0}</p>
         ${warningsHtml(r.warnings)}
         ${classTablesHtml(r.classes)}`
    );
}

function buildWorkspaceHtml(r: WorkspaceReport): string {
    const sp  = r.springReport;
    const fileCount   = r.files?.length ?? 0;
    const classCount  = r.files?.reduce((s, f) => s + (f.classes?.length ?? 0), 0) ?? 0;
    const methodCount = r.files?.reduce((s, f) =>
        s + f.classes?.reduce((s2, c) => s2 + (c.methods?.length ?? 0), 0), 0) ?? 0;

    return wrapHtml(`
        <h2>Java Analyzer — Workspace</h2>
        <p>ファイル数: ${fileCount} / クラス数: ${classCount} / メソッド数: ${methodCount}</p>
        ${warningsHtml(r.warnings)}

        <h3>🌐 エンドポイント (${sp?.endpoints?.length ?? 0})</h3>
        ${endpointsHtml(sp?.endpoints)}

        <h3>🫘 Bean 一覧 (${sp?.beans?.length ?? 0})</h3>
        ${beansHtml(sp?.beans)}

        <h3>🔗 DI グラフ (${sp?.diGraph?.length ?? 0})</h3>
        ${diGraphHtml(sp?.diGraph)}

        <h3>💾 @Transactional メソッド (${sp?.transactionalMethods?.length ?? 0})</h3>
        ${transactionalHtml(sp?.transactionalMethods)}

        <h3>🗄 MyBatis XML マッパー (${r.mybatisReport?.xmlMappers?.length ?? 0} ファイル)</h3>
        ${mybatisHtml(r.mybatisReport)}

        <h3>🔴 デッドコード候補 (${r.deadCodeCandidates?.length ?? 0})</h3>
        ${codeListHtml(r.deadCodeCandidates)}

        <h3>🚪 エントリーポイント候補 (${r.entryPointCandidates?.length ?? 0})</h3>
        ${codeListHtml(r.entryPointCandidates)}

        <h3>📋 重複ブロック (${r.duplicateBlocks?.length ?? 0})</h3>
        ${duplicateBlocksHtml(r.duplicateBlocks)}

        <h3>📄 ファイル別メトリクス</h3>
        ${r.files?.map(f =>
            `<details><summary>${f.filePath} (${f.totalLines} 行)</summary>
             ${warningsHtml(f.warnings)}${classTablesHtml(f.classes)}</details>`
        ).join('') ?? ''}
    `);
}

function endpointsHtml(endpoints?: EndpointInfo[]): string {
    if (!endpoints?.length) return '<p class="none">なし</p>';
    const rows = endpoints.map(e =>
        `<tr><td><b>${e.httpMethod}</b></td><td>${e.path}</td>
             <td>${e.handlerClass}</td><td>${e.handlerMethod}</td><td>${e.line}</td></tr>`
    ).join('');
    return `<table><thead><tr><th>HTTP</th><th>パス</th><th>クラス</th><th>メソッド</th><th>行</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function beansHtml(beans?: BeanInfo[]): string {
    if (!beans?.length) return '<p class="none">なし</p>';
    const rows = beans.map(b =>
        `<tr><td>${b.className}</td><td>${b.beanType}</td></tr>`
    ).join('');
    return `<table><thead><tr><th>クラス名</th><th>種別</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function diGraphHtml(edges?: DiEdge[]): string {
    if (!edges?.length) return '<p class="none">なし</p>';
    const rows = edges.map(e =>
        `<tr><td>${e.from}</td><td>${e.to}</td><td>${e.fieldName}</td><td>${e.injectionType}</td></tr>`
    ).join('');
    return `<table><thead><tr><th>依存元</th><th>注入型</th><th>フィールド名</th><th>注入方式</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function transactionalHtml(methods?: string[]): string {
    if (!methods?.length) return '<p class="none">なし</p>';
    return `<ul>${methods.map(m => `<li>${m}</li>`).join('')}</ul>`;
}

function mybatisHtml(mb?: MyBatisReport): string {
    if (!mb) return '<p class="none">なし</p>';
    const unmapped = mb.unmappedMethods?.length
        ? `<h4>⚠ XML 未対応メソッド (${mb.unmappedMethods.length})</h4>
           <ul>${mb.unmappedMethods.map(m => `<li class="warn">${m}</li>`).join('')}</ul>` : '';
    const mappers = (mb.xmlMappers ?? []).map(xm => {
        const rows = (xm.statements ?? []).map(s => {
            const dynScore = s.ifTagCount + s.foreachTagCount * 2 + s.chooseTagCount;
            const complexClass = (s.joinCount >= 3 || s.subqueryCount >= 2 || dynScore >= 5) ? ' class="high"' : '';
            return `<tr${complexClass}>
              <td><b>${s.id}</b></td><td>${s.statementType}</td>
              <td>${s.joinCount}</td><td>${s.subqueryCount}</td><td>${s.unionCount}</td>
              <td>${s.ifTagCount}</td><td>${s.foreachTagCount}</td><td>${s.chooseTagCount}</td>
              <td>${s.resultType || '-'}</td>
            </tr>`;
        }).join('');
        return `<details><summary><b>${xm.namespace}</b> (${xm.statements?.length ?? 0} 件)</summary>
          <table><thead><tr>
            <th>id</th><th>種別</th>
            <th>JOIN</th><th>サブクエリ</th><th>UNION</th>
            <th>&lt;if&gt;</th><th>&lt;foreach&gt;</th><th>&lt;choose&gt;</th>
            <th>resultType</th>
          </tr></thead><tbody>${rows}</tbody></table>
        </details>`;
    }).join('');
    return unmapped + mappers;
}

function warningsHtml(warnings?: string[]): string {
    if (!warnings?.length) return '';
    return `<div class="warn">⚠ ${warnings.join('<br>')}</div>`;
}

function classTablesHtml(classes?: ClassReport[]): string {
    return (classes ?? []).map(cls => {
        const rows = (cls.methods ?? []).map(m => {
            const nestClass = m.maxNestDepth >= 4   ? ' class="high"' : '';
            const lineClass = m.lineCount >= 40     ? ' class="high"' : '';
            const extClass  = m.externalCallCount >= 5 ? ' class="high"' : '';
            const magics    = m.repeatedNumericLiterals?.join(', ') || '-';
            const cats      = m.externalCallCategories?.join(', ') || '-';
            const abCount   = m.assignmentBlocks?.length ?? 0;
            return `<tr>
              <td>${m.name}</td><td>${m.kind}</td><td>${m.scope}</td>
              <td>${m.startLine}</td><td${lineClass}>${m.lineCount}</td>
              <td${nestClass}>${m.maxNestDepth}</td><td>${m.localDeclCount}</td>
              <td>${m.lambdaCount}</td><td>${m.maxMethodChainDepth}</td>
              <td${extClass}>${m.externalCallCount}</td><td>${cats}</td>
              <td>${m.ioSideEffectCount}</td><td>${m.optionalDirectGet}</td>
              <td>${magics}</td><td>${abCount}</td><td>${m.referenceCount ?? 0}</td>
            </tr>`;
        }).join('');
        return `<h4>${cls.scope} ${cls.kind} <b>${cls.className}</b> (L${cls.startLine}–${cls.endLine})</h4>
                <table><thead><tr>
                  <th>メソッド</th><th>種別</th><th>スコープ</th>
                  <th>開始行</th><th>行数</th><th>ネスト深度</th>
                  <th>ローカル変数</th><th>ラムダ</th><th>チェーン深度</th>
                  <th>外部呼出</th><th>カテゴリ</th>
                  <th>IO副作用</th><th>Optional.get</th>
                  <th>マジック数</th><th>代入ブロック</th><th>参照数</th>
                </tr></thead><tbody>${rows}</tbody></table>
                ${prefixClustersHtml(cls.prefixClusters)}`;
    }).join('');
}

function prefixClustersHtml(clusters?: PrefixCluster[]): string {
    if (!clusters?.length) return '';
    const rows = clusters.map(c =>
        `<tr><td><b>${c.prefix}</b></td><td>${c.identifiers?.join(', ')}</td></tr>`
    ).join('');
    return `<details><summary>プレフィックスクラスタ (${clusters.length})</summary>
      <table><thead><tr><th>プレフィックス</th><th>識別子</th></tr></thead><tbody>${rows}</tbody></table>
    </details>`;
}

function codeListHtml(items?: string[]): string {
    if (!items?.length) return '<p class="none">なし</p>';
    return `<ul>${items.map(i => `<li><code>${i}</code></li>`).join('')}</ul>`;
}

function duplicateBlocksHtml(blocks?: DuplicateBlock[]): string {
    if (!blocks?.length) return '<p class="none">なし</p>';
    return blocks.map(b =>
        `<details><summary>${b.stmtCount} ステートメント — <code>${b.preview}</code> (${b.locations?.length ?? 0} 箇所)</summary>
         <ul>${(b.locations ?? []).map(l => `<li>${l}</li>`).join('')}</ul></details>`
    ).join('');
}

function wrapHtml(body: string): string {
    return `<!DOCTYPE html><html><head><meta charset="UTF-8">
    <style>
      body    { font-family: var(--vscode-font-family); font-size: 13px;
                color: var(--vscode-foreground); background: var(--vscode-editor-background); padding: 12px; }
      h2,h3   { color: var(--vscode-textLink-foreground); margin: 12px 0 6px; }
      h4      { margin: 14px 0 4px; }
      table   { border-collapse: collapse; width: 100%; margin-bottom: 8px; }
      th      { background: var(--vscode-editor-lineHighlightBackground); padding: 4px 8px; text-align: left; white-space: nowrap; }
      td      { padding: 3px 8px; border-bottom: 1px solid var(--vscode-editorGroup-border); }
      details { margin-bottom: 6px; }
      summary { cursor: pointer; color: var(--vscode-textLink-foreground); }
      ul      { margin: 4px 0 8px 20px; }
      .warn   { color: var(--vscode-errorForeground); margin-bottom: 8px; }
      .none   { color: var(--vscode-descriptionForeground); font-style: italic; }
      .high   { color: #f5a623; font-weight: bold; }
    </style></head><body>${body}</body></html>`;
}

// 旧 buildHtml は buildFileHtml に統合済み。後方互換のため残す。
// eslint-disable-next-line @typescript-eslint/no-unused-vars
function buildHtml(r: FileReport): string { return buildFileHtml(r); }
