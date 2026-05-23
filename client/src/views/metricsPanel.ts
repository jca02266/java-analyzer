import * as vscode from 'vscode';

interface ParameterInfo { name: string; type: string; }
interface MethodMetrics {
    name: string; kind: string; scope: string;
    startLine: number; endLine: number; lineCount: number; effectiveLineCount: number;
    maxNestDepth: number; localDeclCount: number;
    lambdaCount: number; maxMethodChainDepth: number;
    methodReferenceCount: number;
    referenceCount: number;
    optionalDirectGet: number;
    equalsTypeMismatchCount: number;
    nullSafetyIssueCount: number;
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
    filePath: string; totalLines: number; effectiveLines: number;
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
interface ThymeleafLinkInfo { templatePath: string; href: string; linkType: string; line: number; resolvedEndpoint?: string; }
interface HtmlStructureInfo { templatePath: string; maxDomDepth: number; formCount: number; inlineScriptCount: number; }
interface JsAjaxCallInfo    { sourcePath: string; url: string; method: string; line: number; resolvedEndpoint?: string; }
interface ThymeleafReport   { links: ThymeleafLinkInfo[]; structures: HtmlStructureInfo[]; ajaxCalls: JsAjaxCallInfo[]; unresolvedLinks: string[]; unresolvedAjaxCalls: string[]; }
interface CallEdge       { from: string; to: string; resolved: boolean; }
interface DuplicateBlock { stmtCount: number; preview: string; locations: string[]; }
interface WorkspaceReport {
    files: FileReport[];
    springReport: SpringReport;
    mybatisReport: MyBatisReport;
    thymeleafReport: ThymeleafReport;
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

    // Backwards compatibility
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
         <p><b>${r.filePath}</b> — Total lines: ${r.totalLines} / Classes: ${r.classes?.length ?? 0}</p>
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
        <p>Files: ${fileCount} / Classes: ${classCount} / Methods: ${methodCount}</p>
        ${warningsHtml(r.warnings)}

        <h3>🌐 Endpoints (${sp?.endpoints?.length ?? 0})</h3>
        ${endpointsHtml(sp?.endpoints)}

        <h3>🫘 Beans (${sp?.beans?.length ?? 0})</h3>
        ${beansHtml(sp?.beans)}

        <h3>🔗 DI Graph (${sp?.diGraph?.length ?? 0})</h3>
        ${diGraphHtml(sp?.diGraph)}

        <h3>💾 @Transactional Methods (${sp?.transactionalMethods?.length ?? 0})</h3>
        ${transactionalHtml(sp?.transactionalMethods)}

        <h3>🍃 Thymeleaf Links (${r.thymeleafReport?.links?.length ?? 0})</h3>
        ${thymeleafLinksHtml(r.thymeleafReport?.links)}

        <h3>🏗 HTML Structure (${r.thymeleafReport?.structures?.length ?? 0})</h3>
        ${htmlStructureHtml(r.thymeleafReport?.structures)}

        <h3>⚡ JavaScript Ajax Calls (${r.thymeleafReport?.ajaxCalls?.length ?? 0})</h3>
        ${jsAjaxCallsHtml(r.thymeleafReport?.ajaxCalls)}

        <h3>🗄 MyBatis XML Mappers (${r.mybatisReport?.xmlMappers?.length ?? 0} files)</h3>
        ${mybatisHtml(r.mybatisReport)}

        <h3>🔴 Dead Code Candidates (${r.deadCodeCandidates?.length ?? 0})</h3>
        ${codeListHtml(r.deadCodeCandidates)}

        <h3>🚪 Entry Point Candidates (${r.entryPointCandidates?.length ?? 0})</h3>
        ${codeListHtml(r.entryPointCandidates)}

        <h3>📋 Duplicate Blocks (${r.duplicateBlocks?.length ?? 0})</h3>
        ${duplicateBlocksHtml(r.duplicateBlocks)}

        <h3>📄 File Metrics</h3>
        ${r.files?.map(f =>
            `<details><summary>${f.filePath} (${f.totalLines} lines)</summary>
             ${warningsHtml(f.warnings)}${classTablesHtml(f.classes)}</details>`
        ).join('') ?? ''}
    `);
}

function endpointsHtml(endpoints?: EndpointInfo[]): string {
    if (!endpoints?.length) return '<p class="none">None</p>';
    const rows = endpoints.map(e =>
        `<tr><td><b>${e.httpMethod}</b></td><td>${e.path}</td>
             <td>${e.handlerClass}</td><td>${e.handlerMethod}</td><td>${e.line}</td></tr>`
    ).join('');
    return `<table><thead><tr><th>HTTP</th><th>Path</th><th>Class</th><th>Method</th><th>Line</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function beansHtml(beans?: BeanInfo[]): string {
    if (!beans?.length) return '<p class="none">None</p>';
    const rows = beans.map(b =>
        `<tr><td>${b.className}</td><td>${b.beanType}</td></tr>`
    ).join('');
    return `<table><thead><tr><th>Class Name</th><th>Type</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function diGraphHtml(edges?: DiEdge[]): string {
    if (!edges?.length) return '<p class="none">None</p>';
    const rows = edges.map(e =>
        `<tr><td>${e.from}</td><td>${e.to}</td><td>${e.fieldName}</td><td>${e.injectionType}</td></tr>`
    ).join('');
    return `<table><thead><tr><th>From</th><th>To</th><th>Field Name</th><th>Injection Type</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function transactionalHtml(methods?: string[]): string {
    if (!methods?.length) return '<p class="none">None</p>';
    return `<ul>${methods.map(m => `<li>${m}</li>`).join('')}</ul>`;
}

function mybatisHtml(mb?: MyBatisReport): string {
    if (!mb) return '<p class="none">None</p>';
    const unmapped = mb.unmappedMethods?.length
        ? `<h4>⚠ Unmapped Methods (${mb.unmappedMethods.length})</h4>
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
        return `<details><summary><b>${xm.namespace}</b> (${xm.statements?.length ?? 0} statements)</summary>
          <table><thead><tr>
            <th>ID</th><th>Type</th>
            <th>JOIN</th><th>Subquery</th><th>UNION</th>
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
              <td>${m.startLine}</td><td${lineClass}>${m.lineCount}</td><td>${m.effectiveLineCount}</td>
              <td${nestClass}>${m.maxNestDepth}</td><td>${m.localDeclCount}</td>
              <td>${m.lambdaCount}</td><td>${m.maxMethodChainDepth}</td>
              <td${extClass}>${m.externalCallCount}</td><td>${cats}</td>
              <td>${m.ioSideEffectCount}</td><td>${m.optionalDirectGet}</td><td>${m.equalsTypeMismatchCount}</td>
              <td>${m.nullSafetyIssueCount}</td><td>${magics}</td><td>${abCount}</td><td>${m.referenceCount ?? 0}</td>
            </tr>`;
        }).join('');
        return `<h4>${cls.scope} ${cls.kind} <b>${cls.className}</b> (L${cls.startLine}–${cls.endLine})</h4>
                <table><thead><tr>
                  <th>Method</th><th>Type</th><th>Scope</th>
                  <th>Start Line</th><th>Line Count</th><th>Eff. Lines</th><th>Nesting Depth</th>
                  <th>Local Vars</th><th>Lambdas</th><th>Chain Depth</th>
                  <th>External Calls</th><th>Categories</th>
                  <th>IO Side Effects</th><th>Optional.get</th><th>Eq.Mismatch</th><th>Null Issues</th>
                  <th>Magic Numbers</th><th>Assignment Blocks</th><th>References</th>
                </tr></thead><tbody>${rows}</tbody></table>
                ${prefixClustersHtml(cls.prefixClusters)}`;
    }).join('');
}

function prefixClustersHtml(clusters?: PrefixCluster[]): string {
    if (!clusters?.length) return '';
    const rows = clusters.map(c =>
        `<tr><td><b>${c.prefix}</b></td><td>${c.identifiers?.join(', ')}</td></tr>`
    ).join('');
    return `<details><summary>Prefix Clusters (${clusters.length})</summary>
      <table><thead><tr><th>Prefix</th><th>Identifiers</th></tr></thead><tbody>${rows}</tbody></table>
    </details>`;
}

function codeListHtml(items?: string[]): string {
    if (!items?.length) return '<p class="none">None</p>';
    return `<ul>${items.map(i => `<li><code>${i}</code></li>`).join('')}</ul>`;
}

function thymeleafLinksHtml(links?: ThymeleafLinkInfo[]): string {
    if (!links?.length) return '<p class="none">None</p>';
    const rows = links.map(link => {
        const resolved = link.resolvedEndpoint ? '' : ' class="high"';
        return `<tr${resolved}>
          <td>${link.templatePath}</td><td>${link.linkType}</td><td><code>${link.href}</code></td>
          <td>${link.line}</td><td>${link.resolvedEndpoint || 'Unresolved'}</td></tr>`;
    }).join('');
    return `<table><thead><tr><th>Template</th><th>Type</th><th>Link</th><th>Line</th><th>Endpoint</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function htmlStructureHtml(structures?: HtmlStructureInfo[]): string {
    if (!structures?.length) return '<p class="none">None</p>';
    const rows = structures.map(s => {
        const depthClass = s.maxDomDepth >= 8 ? ' class="high"' : '';
        return `<tr${depthClass}>
          <td>${s.templatePath}</td><td>${s.maxDomDepth}</td><td>${s.formCount}</td><td>${s.inlineScriptCount}</td></tr>`;
    }).join('');
    return `<table><thead><tr><th>Template</th><th>Max DOM Depth</th><th>Forms</th><th>Scripts</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function jsAjaxCallsHtml(calls?: JsAjaxCallInfo[]): string {
    if (!calls?.length) return '<p class="none">None</p>';
    const rows = calls.map(call => {
        const resolved = call.resolvedEndpoint ? '' : ' class="high"';
        return `<tr${resolved}>
          <td>${call.sourcePath}</td><td>${call.method}</td><td><code>${call.url}</code></td>
          <td>${call.line}</td><td>${call.resolvedEndpoint || 'Unresolved'}</td></tr>`;
    }).join('');
    return `<table><thead><tr><th>Source</th><th>Method</th><th>URL</th><th>Line</th><th>Endpoint</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function duplicateBlocksHtml(blocks?: DuplicateBlock[]): string {
    if (!blocks?.length) return '<p class="none">None</p>';
    return blocks.map(b =>
        `<details><summary>${b.stmtCount} statements — <code>${b.preview}</code> (${b.locations?.length ?? 0} locations)</summary>
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

// Legacy: buildHtml was merged into buildFileHtml. Kept for backwards compatibility.
// eslint-disable-next-line @typescript-eslint/no-unused-vars
function buildHtml(r: FileReport): string { return buildFileHtml(r); }
