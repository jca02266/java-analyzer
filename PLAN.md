# java-analyzer 実装計画

## 概要

Spring Boot / MyBatis / Lombok を使った Java ソースコードを静的解析する VS Code 拡張。
リファクタリング候補の抽出と、フレームワーク固有の構造把握（エンドポイント・SQL・DI グラフ）の両方を目的とする。

VBA アナライザー（`vba-analyzer-java.md`）の設計を参考に、Java 向けに応用した実装。

---

## アーキテクチャ

```
┌────────────────────────────────────┐
│  VS Code 拡張 (TypeScript)          │
│  vscode-languageclient              │
│  WebView（メトリクス・構造ビュー）   │
└──────────────┬─────────────────────┘
               │ LSP + カスタムコマンド (stdio)
┌──────────────▼─────────────────────┐
│  Java Language Server               │
│  lsp4j                              │
│  ┌─────────────────────────────┐   │
│  │ delombok 前処理              │   │
│  │ JavaParser + SymbolSolver   │   │
│  │ Spring アノテーション解析    │   │
│  │ MyBatis XML パーサー        │   │
│  └─────────────────────────────┘   │
└────────────────────────────────────┘
```

### 設計方針

- LSP サーバー自体は **Java 17** でビルド・動作
- 解析対象は **Java 8** をデフォルト（設定で Java 11 / 17 / 21 に変更可能）
- JVM は VS Code 起動時に一度だけ起動し、常駐させる（起動コストは初回のみ）
- ユーザー環境に Java 17+ が必要

---

## 技術スタック

| レイヤー | ライブラリ / ツール | バージョン |
|---------|-------------------|-----------|
| LSP サーバー（Java） | lsp4j | 0.23.x |
| Java 解析・型解決 | JavaParser + JavaSymbolSolver | 3.25.x |
| Lombok 前処理 | delombok（Lombok CLI） | プロジェクトの Lombok に合わせる |
| MyBatis XML 解析 | Java 標準 DOM パーサー | - |
| JSON 出力 | Gson | 2.10.x |
| LSP クライアント（TypeScript） | vscode-languageclient | 9.x |
| ビルド | Maven | 3.8+ |

---

## プロジェクト構成

```
java-analyzer/
├── PLAN.md
├── package.json                   ← VS Code 拡張マニフェスト
├── tsconfig.json
├── client/src/
│   ├── extension.ts               ← Language Client・Java プロセス起動
│   └── views/
│       └── metricsPanel.ts        ← WebView（メトリクス表示）
└── server/                        ← Java Language Server
    ├── pom.xml
    └── src/main/java/javaanalyzer/
        ├── Main.java                        ← stdio で LSP 起動
        ├── AnalyzerLanguageServer.java      ← lsp4j サーバー実装
        ├── analyzer/
        │   ├── JavaFileAnalyzer.java        ← 単一ファイル解析
        │   └── WorkspaceAnalyzer.java       ← 複数ファイル集約
        ├── metrics/
        │   ├── MethodMetrics.java
        │   ├── ClassReport.java
        │   ├── FileReport.java
        │   └── WorkspaceReport.java
        ├── spring/
        │   ├── SpringAnnotationVisitor.java ← Bean/DI/Endpoint 抽出
        │   └── SpringReport.java
        ├── mybatis/
        │   ├── MyBatisXmlParser.java        ← XML マッパー解析
        │   ├── MapperCorrelator.java        ← Java ↔ XML 対応付け
        │   └── MyBatisReport.java
        └── visitors/
            ├── MethodCollector.java         ← メソッド一覧収集
            ├── NestDepthVisitor.java        ← ネスト深度計測
            ├── ExternalCallVisitor.java     ← 外部依存検出
            ├── LambdaStreamVisitor.java     ← ラムダ・Stream 解析
            ├── AssignmentBlockVisitor.java  ← 連続代入ブロック
            ├── DuplicateBlockDetector.java  ← 重複ブロック検出
            └── PrefixClusterDetector.java   ← 接頭辞クラスター
```

---

## 出力データ構造

### WorkspaceReport（ワークスペース全体）

```
WorkspaceReport
├── files[]                     ← FileReport（Java ファイル単位）
├── springReport
│   ├── endpoints[]             ← パス・HTTPメソッド・ハンドラメソッド
│   ├── beans[]                 ← Bean 一覧（種別・クラス名）
│   ├── diGraph[]               ← 依存注入グラフ（from → to）
│   └── transactionalMethods[]  ← @Transactional メソッド一覧
├── mybatisReport
│   ├── xmlMappers[]            ← XML ファイル単位の解析結果
│   │   └── statements[]        ← SQL 文・種別・複雑度・JOIN 数
│   └── unmappedMethods[]       ← XML 未対応の Mapper メソッド
├── callGraph[]                 ← プロシージャ間呼び出しエッジ
├── duplicateBlocks[]           ← 重複コードブロック
└── deadCodeCandidates[]        ← デッドコード候補
```

### FileReport（ファイル単位）

| フィールド | 内容 |
|-----------|------|
| `filePath` | ファイルパス |
| `totalLines` | 総行数 |
| `classes[]` | クラス単位の解析結果 |
| `warnings[]` | パースエラー・警告 |

### ClassReport（クラス単位）

| フィールド | 内容 |
|-----------|------|
| `className` | クラス名 |
| `kind` | `class` / `interface` / `enum` / `annotation` |
| `scope` | `public` / `package-private` |
| `methods[]` | メソッドメトリクス一覧 |
| `springAnnotations` | `@Controller` / `@Service` / `@Repository` / `@Component` 等 |
| `lombokAnnotations` | `@Data` / `@Builder` / `@Value` 等（使用状況） |
| `mapperMethods[]` | `@Mapper` インターフェースのメソッド一覧 |
| `prefixClusters[]` | 接頭辞クラスター（定数・フィールド群） |

### MethodMetrics（メソッド単位）

| フィールド | 内容 |
|-----------|------|
| `name` | メソッド名 |
| `kind` | `method` / `constructor` |
| `scope` | `public` / `private` / `protected` / `package` |
| `startLine` / `endLine` | 行番号 |
| `lineCount` | メソッド行数 |
| `maxNestDepth` | if/for/while/switch/try のネスト深度 |
| `localDeclCount` | ローカル変数宣言数 |
| `lambdaCount` | ラムダ式の数 |
| `streamChainDepth` | Stream チェーンの最大長 |
| `optionalDirectGet` | `Optional.get()` 直呼び回数（警告対象） |
| `methodReferenceCount` | メソッド参照の数 |
| `externalCallCount` | JDBC / HttpClient / File I/O 呼び出し数（型解決済み） |
| `ioSideEffectCount` | `System.out` / `Logger` / `Scanner` 呼び出し数 |
| `repeatedNumericLiterals` | マジックナンバー（同一値が 2 回以上） |
| `assignmentBlocks[]` | 連続代入ブロック（5 行以上） |
| `parameters[]` | パラメーター名・型（SymbolSolver で解決） |
| `returnType` | 戻り値型（解決済み） |
| `referenceCount` | 他メソッドからの呼び出し回数 |
| `springMeta` | `@Transactional` / `@GetMapping` 等のメタ情報 |

---

## カスタム LSP コマンド

| コマンド | 内容 |
|---------|------|
| `javaAnalyzer/analyzeFile` | 開いているファイルを解析して `FileReport` を返す |
| `javaAnalyzer/analyzeWorkspace` | ワークスペース全体を解析して `WorkspaceReport` を返す |

---

## VS Code 設定（settings.json）

```json
{
  "javaAnalyzer.languageLevel": "JAVA_8",
  "javaAnalyzer.lombokJarPath": "/path/to/lombok.jar",
  "javaAnalyzer.sourcePaths": ["src/main/java"],
  "javaAnalyzer.classpathEntries": []
}
```

---

## フレームワーク固有の対処方針

### Lombok

delombok で展開前処理を行い、展開後のソースを JavaParser に渡す。

```
解析フロー:
1. java -jar lombok.jar delombok src/main/java -d /tmp/delombok-out
2. JavaParser が /tmp/delombok-out を解析
3. エラー行番号はオリジナルソースにマッピング
```

### Spring Boot

アノテーションベースの静的解析（DI コンテナは起動しない）。

| アノテーション | 取得情報 |
|-------------|---------|
| `@RestController` / `@Controller` | エンドポイント候補クラス |
| `@GetMapping` / `@PostMapping` 等 | HTTPメソッド・パス |
| `@Service` / `@Repository` / `@Component` | Bean 種別 |
| `@Autowired` / `@RequiredArgsConstructor` | DI 依存関係 |
| `@Transactional` | トランザクション境界 |
| `@Aspect` | AOP アドバイス一覧 |

### MyBatis XML

```
相関付けフロー:
1. **/*.xml を走査して namespace を抽出
2. namespace に一致する @Mapper インターフェースを特定
3. XML の <select id="xxx"> と Mapper メソッド名を照合
4. 未対応メソッド（XML なし）を unmappedMethods に記録
```

SQL 複雑度指標：

| 指標 | 内容 |
|------|------|
| `joinCount` | JOIN 句の数 |
| `subqueryCount` | サブクエリの数 |
| `unionCount` | UNION の数 |
| `ifTagCount` | MyBatis `<if>` タグの数（動的 SQL 複雑度） |
| `foreachTagCount` | MyBatis `<foreach>` タグの数 |

---

## 実装フェーズ

### Phase 0 — プロジェクト骨格（疎通確認）

- [ ] `server/pom.xml`（lsp4j + JavaParser + SymbolSolver + Gson）
- [ ] `package.json`（VS Code 拡張マニフェスト）
- [ ] `client/src/extension.ts`（Java プロセス起動・Language Client 接続）
- [ ] `server/.../Main.java`（LSP サーバー起動）
- [ ] `server/.../AnalyzerLanguageServer.java`（ping/pong カスタムコマンド）
- [ ] `mvn package` でビルド確認・VS Code から接続確認

### Phase 1 — 基本 Java メトリクス

- [ ] delombok 前処理の組み込み
- [ ] `MethodCollector`：メソッド一覧・行数・スコープ・パラメーター
- [ ] `NestDepthVisitor`：if/for/while/switch/try のネスト深度
- [ ] `LambdaStreamVisitor`：ラムダ数・Stream チェーン深度・Optional.get() 検出
- [ ] `javaAnalyzer/analyzeFile` コマンド実装
- [ ] WebView にメトリクス表示

### Phase 2 — Spring Boot 解析

- [ ] `SpringAnnotationVisitor`：Bean 種別・DI グラフ・エンドポイント抽出
- [ ] `@Transactional` メソッド一覧
- [ ] `springReport` を `WorkspaceReport` に組み込み
- [ ] WebView にエンドポイント一覧・DI グラフ表示

### Phase 3 — MyBatis XML 解析

- [ ] `MyBatisXmlParser`：XML マッパーファイルの走査・SQL 解析
- [ ] `MapperCorrelator`：Java Mapper インターフェース ↔ XML 対応付け
- [ ] SQL 複雑度指標（JOIN 数・サブクエリ数・動的 SQL タグ数）
- [ ] 未対応メソッド（unmappedMethods）の検出

### Phase 4 — 型解決を活用した詳細メトリクス

- [ ] JavaSymbolSolver のクラスパス設定
- [ ] `ExternalCallVisitor`：JDBC / HttpClient / File I/O の型解決による確実な検出
- [ ] IO 副作用（`System.out` / `Logger` / `Scanner`）カウント
- [ ] マジックナンバー・連続代入ブロック
- [ ] `AssignmentBlockVisitor`：5 行以上の連続代入ブロック検出・形状分類

### Phase 5 — クロスファイル解析

- [ ] `WorkspaceAnalyzer`：複数ファイルを集約
- [ ] コールグラフ（メソッド間の呼び出しエッジ）
- [ ] デッドコード候補（`referenceCount == 0` かつ非エントリポイント）
- [ ] `DuplicateBlockDetector`：正規化 N-gram マッチングで重複ブロック検出
- [ ] `PrefixClusterDetector`：定数・フィールドの接頭辞クラスター
- [ ] `javaAnalyzer/analyzeWorkspace` コマンド実装

---

## 追加機能 TODO

指示があった都度ここに追記する。実装フェーズへの組み込みは優先度を見て判断。

- [ ] **有効行数計算**：空行・コメント行（`//` 行・`/* */` ブロック・`/** */` Javadoc）を除いた実装行数を計測
  - ファイル単位の `effectiveLines` とメソッド単位の `effectiveLineCount` を追加
  - 総行数との比率（コメント率）も合わせて出力
  - 実装方針：JavaParser のトークンリストまたはソースを行単位でスキャンして計測

- [ ] **equals 型安全チェック**：`Object.equals()` の引数と被呼び出しオブジェクトの型が一致しているか検証（型解決が必要）。型不一致は常に `false` になるバグの温床のため警告対象とする
  - 例：`stringVar.equals(intVar)` → 型不一致として報告
  - 対象：`equals` / `equalsIgnoreCase` / `Objects.equals(a, b)`

- [ ] **Thymeleaf / HTML / JavaScript 解析**：Spring Boot プロジェクトではビューレイヤーの解析も必要
  - Thymeleaf テンプレート（`*.html`）：`th:*` 属性の使用状況・Controller との対応付け（`th:href`/`th:action` のエンドポイント参照）
  - HTML：構造解析（DOM ツリー深度・インラインスクリプト検出）
  - JavaScript（`*.js` / `<script>` ブロック）：Ajax リクエスト先 URL と Controller エンドポイントとの照合
  - 実装方針：Java 側は HTML/JS を DOM パーサーで解析、TypeScript 側は既存の Tree-sitter（JS 文法）を活用する選択肢もあり

- [ ] **null 安全チェック**：null 参照の危険箇所を検出
  - `Optional.get()` の直呼び（isPresent チェックなし）
  - null を返す可能性があるメソッドの戻り値を、null チェックなしで直接呼び出しているケース
  - `@NonNull` / `@NotNull` アノテーションが付いたパラメーターへの null リテラル渡し
  - MyBatis Mapper の戻り値（`selectOne` 等）を null チェックなしで使用しているケース

- [ ] **出力メッセージの英語化**：サーバー・クライアント双方の出力メッセージをすべて英語に統一する
  - Java サーバー側：warnings / error メッセージ（`FileReport.warnings`、`WorkspaceReport.warnings` に格納される文字列）
  - TypeScript クライアント側：`vscode.window.showWarningMessage` / `showErrorMessage` / `showInformationMessage` の表示文字列
  - WebView パネル（`metricsPanel.ts`）：テーブルヘッダー・ラベル・セクション見出し等の日本語テキスト

---

## 実装優先順位の目安

1. **Phase 0-1**：基本的なメトリクス表示。リファクタリング候補の大まかな把握
2. **Phase 2**：Spring Boot 構造の可視化
3. **Phase 3**：MyBatis SQL 複雑度の把握
4. **Phase 4**：型解決による精度向上
5. **Phase 5**：ワークスペース全体の品質評価
