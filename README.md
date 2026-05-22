# Java Analyzer

Spring Boot / MyBatis / Lombok を使った Java プロジェクトを静的解析する VS Code 拡張。

リファクタリング候補の抽出と、フレームワーク固有の構造把握（エンドポイント・SQL・DI グラフ）の両方を目的とする。

---

## 機能

### メトリクス解析（ファイル・メソッド単位）

| 指標 | 内容 |
|------|------|
| 行数 | メソッドの総行数 |
| ネスト深度 | if / for / while / switch / try の最大ネスト深度 |
| ラムダ数 | ラムダ式の使用数 |
| Stream チェーン深度 | メソッドチェーンの最大長 |
| 外部呼び出し | JDBC / HTTP / File I/O の呼び出し数と種別 |
| IO 副作用 | `System.out` / Logger / Scanner の使用数 |
| `Optional.get()` | isPresent チェックなしの直呼び回数 |
| マジックナンバー | 2 回以上出現する数値リテラル |
| 連続代入ブロック | 5 行以上連続する変数宣言・代入のブロック |
| 参照数 | 他メソッドからの内部呼び出し回数 |

### Spring Boot 解析

- エンドポイント一覧（HTTP メソッド・パス・ハンドラクラス・行番号）
- Bean 一覧（`@Service` / `@Repository` / `@Controller` / `@Configuration` / `@Bean` 等）
- DI グラフ（`@Autowired` フィールド注入・コンストラクタ注入）
- `@Transactional` メソッド一覧

### MyBatis XML 解析

- XML マッパーファイルの走査と SQL 解析
- SQL 複雑度指標（JOIN 数・サブクエリ数・UNION 数・`<if>` / `<foreach>` / `<choose>` タグ数）
- Java `@Mapper` インターフェース ↔ XML の対応付け
- XML 未対応メソッドの検出（`unmappedMethods`）

### クロスファイル解析

- **コールグラフ**：メソッド間の呼び出しエッジ
- **デッドコード候補**：`private` かつ呼び出し元がないメソッド
- **エントリーポイント候補**：`public` かつ内部呼び出しがないメソッド
- **重複ブロック**：正規化 N-gram マッチングによる類似コード検出（3 ステートメント以上）
- **プレフィックスクラスタ**：`STATUS_*` / `ROLE_*` などの定数群の検出

### 定義ジャンプ（Go to Definition）※実装予定

Java コードと MyBatis XML を横断した定義ジャンプを LSP で提供する。

| ジャンプ方向 | 操作 | 結果 |
|-------------|------|------|
| Java → Java | `@Mapper` メソッド以外のシンボルで F12 | プロジェクト内の宣言元へ |
| Java → XML | `@Mapper` インターフェースのメソッドで F12 | 対応する `<select id="...">` 行へ |
| XML → Java | `<select id="findById">` の id 上で F12 | `@Mapper` インターフェースのメソッド行へ |

---

## 必要環境

| 項目 | バージョン |
|------|-----------|
| Java（実行環境） | 11 以上 |
| Node.js | 18 以上 |
| VS Code | 1.85 以上 |
| Maven | 3.8 以上（ビルド時のみ） |

---

## セットアップ

### 1. Java サーバーのビルド

```bash
cd server
mvn package -q
# → server/target/java-analyzer-server-1.0-SNAPSHOT.jar が生成される
```

### 2. TypeScript クライアントのビルド

```bash
npm install
npm run compile
```

### 3. VS Code 拡張として起動

VS Code でこのリポジトリを開き、**F5** を押すと拡張開発ホストが起動する。

---

## 使い方（VS Code）

Java ファイルを開いた状態でコマンドパレット（`Cmd+Shift+P`）を開く。

| コマンド | 内容 |
|---------|------|
| `Java Analyzer: Analyze File` | 現在のファイルを解析してメトリクスをパネル表示 |
| `Java Analyzer: Analyze Workspace` | ワークスペース全体を解析（Spring/MyBatis/クロスファイル含む） |

解析結果はサイドパネルに表示される。複雑度が高い箇所はオレンジ色でハイライトされる。

### 設定（settings.json）

```json
{
  "javaAnalyzer.languageLevel": "JAVA_8",
  "javaAnalyzer.lombokJarPath": "/path/to/lombok.jar"
}
```

| 設定 | デフォルト | 説明 |
|------|-----------|------|
| `javaAnalyzer.languageLevel` | `JAVA_8` | 解析対象の Java 言語レベル（`JAVA_8` / `JAVA_11` / `JAVA_17` / `JAVA_21`） |
| `javaAnalyzer.lombokJarPath` | （空） | delombok 前処理用 lombok.jar のパス |
| `javaAnalyzer.javaDefinition` | `auto` | Java 定義ジャンプの有効化（後述） |

---

## 定義ジャンプと他の Java 拡張との関係

### `javaAnalyzer.javaDefinition` 設定

| 値 | 動作 |
|----|------|
| `auto`（デフォルト） | **redhat.java がインストールされていなければ** Java 定義ジャンプを有効化 |
| `enabled` | 常に有効（redhat.java と共存、重複結果が出る場合あり） |
| `disabled` | Java 定義ジャンプを無効化（XML ↔ Java ジャンプは常に有効） |

XML ↔ Java 間のジャンプは redhat.java と競合しないため、この設定に関わらず常に有効。

### redhat.java と共存した場合の動作

両方インストールした状態で F12 を押すと、VS Code が両方の結果をマージして表示する。

```
UserService#register で F12 を押したとき（enabled 時）

redhat.java の結果  → UserService.java:42  ← Java 宣言元
java-analyzer の結果 → UserService.java:42  ← 同じ場所（重複）

→ peek UI に2件表示される（冗長だが機能的な問題はない）
```

```
UserMapper#findById で F12 を押したとき

redhat.java の結果  → UserMapper.java:15   ← Java インターフェース宣言
java-analyzer の結果 → UserMapper.java:15   ← 同じ場所（重複）
                    → UserMapper.xml:28    ← XML 定義（java-analyzer 独自）

→ peek UI に3件表示（XML へのジャンプが追加される）
```

### 注意事項

- **プロジェクト内のクラス・メソッドのみ**対応。Spring Framework や JDK のソースへはジャンプ不可（ソース jar が必要）
- **Lombok 生成メソッド**（`getXxx` / `setXxx` 等）へのジャンプは未対応（delombok 未実装のため）
- シンボル解決には JavaParser の SymbolSolver を使用。型推論が複雑な場合（ジェネリクスのネスト等）に解決失敗することがある
- ワークスペース解析を実行していない場合、XML ↔ Java ジャンプのインデックスが未構築のため動作しない。先に `Java Analyzer: Analyze Workspace` を実行すること

---

## CLI での動作確認

VS Code を使わずに、コマンドラインから解析結果を JSON で確認できる。

```bash
# ワークスペース解析（sample ディレクトリ）
./run-analysis.sh

# 単一ファイル解析
./run-analysis.sh file sample/src/main/java/com/example/demo/service/UserService.java

# 任意のプロジェクトを解析
./run-analysis.sh workspace /path/to/your/springboot-project

# Java バージョンを指定
JAVA_LEVEL=JAVA_11 ./run-analysis.sh workspace /path/to/project
```

直接 jar を実行する場合：

```bash
java -cp server/target/java-analyzer-server-1.0-SNAPSHOT.jar \
  javaanalyzer.RunAnalysis <path> [JAVA_8|JAVA_11|JAVA_17] [file|workspace]
```

---

## サンプルプロジェクト

`sample/` ディレクトリに Spring Boot 風のサンプルソースが含まれている。

```
sample/src/main/java/com/example/demo/
├── controller/
│   ├── UserController.java   (@RestController, @GetMapping/@PostMapping 等)
│   └── OrderController.java
├── service/
│   ├── UserService.java      (@Service, @Transactional, コンストラクタ注入)
│   └── OrderService.java
├── mapper/
│   ├── UserMapper.java       (@Mapper インターフェース)
│   └── OrderMapper.java
├── entity/
│   ├── User.java             (@Data @Builder — Lombok, 定数群)
│   ├── Order.java
│   └── OrderItem.java
└── config/
    └── AppConfig.java        (@Configuration, @Bean)

sample/src/main/resources/mapper/
├── UserMapper.xml            (動的 SQL: <if> タグ, LEFT JOIN)
└── OrderMapper.xml           (サブクエリ, INNER JOIN, <if> タグ)
```

`./run-analysis.sh` を実行すると以下が確認できる：

- エンドポイント 11 件の自動検出
- DI グラフ（コンストラクタ注入・フィールド注入の混在）
- `unmappedMethods`：XML 未定義の `OrderMapper#countPendingOrders`
- デッドコード候補：`UserService#isValidEmail`、`OrderService#isHighValueOrder`
- プレフィックスクラスタ：`STATUS_*`（5 件）、`ROLE_*`（3 件）

---

## アーキテクチャ

```
┌──────────────────────────────────────┐
│  VS Code 拡張 (TypeScript)            │
│  vscode-languageclient 9.x           │
│  WebView（メトリクス・構造ビュー）     │
└─────────────────┬────────────────────┘
                  │ LSP stdio transport
┌─────────────────▼────────────────────┐
│  Java Language Server (lsp4j 0.23)   │
│  ┌──────────────────────────────┐    │
│  │ JavaParser 3.25              │    │
│  │ + JavaSymbolSolver（型解決）  │    │
│  │ Spring アノテーション解析     │    │
│  │ MyBatis XML パーサー         │    │
│  │ クロスファイル解析            │    │
│  └──────────────────────────────┘    │
└──────────────────────────────────────┘
```

| レイヤー | ライブラリ | バージョン |
|---------|-----------|-----------|
| LSP サーバー | lsp4j | 0.23.1 |
| Java 解析・型解決 | JavaParser + SymbolSolver | 3.25.10 |
| JSON 出力 | Gson | 2.10.1 |
| LSP クライアント | vscode-languageclient | 9.x |

---

## ハイライトの閾値

WebView パネルでオレンジ色になる条件：

| 指標 | 閾値 |
|------|------|
| メソッド行数 | 40 行以上 |
| ネスト深度 | 4 以上 |
| 外部呼び出し数 | 5 以上 |
| MyBatis SQL 複雑度 | JOIN ≥ 3、サブクエリ ≥ 2、動的タグスコア ≥ 5 のいずれか |
