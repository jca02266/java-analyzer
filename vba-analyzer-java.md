# VBA静的解析ツール — Java実装ガイド

`test-libs/vba-analyzer.ts`（TypeScript版）と同等の静的解析を Java で実装する手順書。

---

## 1. フォルダ作成とプロジェクト構成

現在のリポジトリとは **独立したディレクトリ**に Maven プロジェクトを作成する。

```
../vba-analyzer-java/          ← 本リポジトリの隣に作成（場所は任意）
├── pom.xml
└── src/
    └── main/
        └── java/
            └── vbaanalyzer/
                ├── Main.java               ← CLI エントリポイント
                ├── VbaFileAnalyzer.java    ← 単一ファイル解析
                ├── WorkspaceAnalyzer.java  ← ワークスペース（複数ファイル）集約
                ├── metrics/
                │   ├── ProcedureMetrics.java
                │   ├── FileReport.java
                │   └── WorkspaceReport.java
                └── visitors/
                    ├── ProcedureCollector.java
                    ├── NestDepthVisitor.java
                    ├── ExcelAccessVisitor.java
                    ├── AssignmentBlockVisitor.java
                    ├── DuplicateBlockDetector.java
                    └── PrefixClusterDetector.java
```

```bash
mkdir -p ../vba-analyzer-java/src/main/java/vbaanalyzer/{metrics,visitors}
cd ../vba-analyzer-java
```

---

## 2. セットアップ

### 2-1. 前提条件

| ツール | バージョン |
|--------|-----------|
| Java   | 17 以上   |
| Maven  | 3.8 以上  |

### 2-2. pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>vbaanalyzer</groupId>
  <artifactId>vba-analyzer-java</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <!-- ANTLR4 ランタイムと文法バージョンを揃える -->
    <antlr4.version>4.13.2</antlr4.version>
  </properties>

  <dependencies>
    <!-- VBA パーサー（ANTLR4 ランタイム） -->
    <dependency>
      <groupId>org.antlr</groupId>
      <artifactId>antlr4-runtime</artifactId>
      <version>${antlr4.version}</version>
    </dependency>
    <!-- JSON 出力 -->
    <dependency>
      <groupId>com.google.code.gson</groupId>
      <artifactId>gson</artifactId>
      <version>2.10.1</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <!-- ANTLR4 文法から Java コードを自動生成 -->
      <plugin>
        <groupId>org.antlr</groupId>
        <artifactId>antlr4-maven-plugin</artifactId>
        <version>${antlr4.version}</version>
        <executions>
          <execution>
            <goals><goal>antlr4</goal></goals>
          </execution>
        </executions>
      </plugin>
      <!-- 実行可能 fat jar -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.0</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation=
                    "org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>vbaanalyzer.Main</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

### 2-3. VBA 文法ファイルの取得

ANTLR4 の公式文法コレクション（grammars-v4）から VBA 文法を取得する。

```bash
# プロジェクト内に文法ディレクトリを作成
mkdir -p src/main/antlr4/vbaanalyzer

# grammars-v4 から VBA 文法をダウンロード
BASE=https://raw.githubusercontent.com/antlr/grammars-v4/master/vba
curl -o src/main/antlr4/vbaanalyzer/VBALexer.g4  $BASE/VBALexer.g4
curl -o src/main/antlr4/vbaanalyzer/VBAParser.g4 $BASE/VBAParser.g4
```

> **文法の特徴**: `grammars-v4/vba` は VBA6 / VBA7 両対応。`startRule` は `startRule`。
> パースエラー時のリカバリも ANTLR4 が自動で行う。

### 2-4. ビルドと実行

```bash
# 文法から Java コードを生成しビルド（初回）
mvn package -q

# テキスト出力
java -jar target/vba-analyzer-java-1.0-SNAPSHOT.jar path/to/file.bas

# JSON 出力
java -jar target/vba-analyzer-java-1.0-SNAPSHOT.jar path/to/dir/ --json
```

---

## 3. vba-analyzer が行っていること

TypeScript 版の `test-libs/vba-analyzer.ts` は VBA ソースを AST（パース木）に変換し、
リファクタリング・テスト設計に役立つ情報を以下のカテゴリに分類して出力する。

### 3-1. 出力データ構造

#### ファイル単位（`FileReport`）

| フィールド             | 内容                                                        |
|-----------------------|-------------------------------------------------------------|
| `filePath`            | 解析対象ファイルパス                                         |
| `totalLines`          | 総行数                                                      |
| `procedureCount`      | Sub/Function/Property の数                                  |
| `procedures[]`        | 各プロシージャの詳細メトリクス（後述）                        |
| `prefixClusters[]`    | 命名接頭辞クラスター（UDT 抽出候補、後述）                    |
| `warnings[]`          | パースエラー等の警告メッセージ                               |

#### プロシージャ単位（`ProcedureMetrics`）

| フィールド                   | 内容                                                               |
|-----------------------------|--------------------------------------------------------------------|
| `name`                      | プロシージャ名                                                      |
| `kind`                      | `Sub` / `Function` / `Property`                                    |
| `scope`                     | `public` / `private` / `friend`                                    |
| `startLine` / `endLine`     | ソース行番号                                                        |
| `lineCount`                 | プロシージャ行数                                                    |
| `maxNestDepth`              | 最大ネスト深度（If/For/While/Select/With を 1 段として計測）         |
| `localDeclCount`            | `Dim` 宣言の変数数                                                  |
| `assignmentBlocks[]`        | 連続代入ブロック（5行以上）の一覧（後述）                            |
| `excelAccessCount`          | Excel オブジェクトへのアクセス回数                                   |
| `excelAccessSamples[]`      | Excel アクセスのサンプル（行番号・式）                               |
| `excelObjectsUsed[]`        | 使用している Excel オブジェクト名（`Sheets`, `Range` 等）            |
| `repeatedNumericLiterals[]` | 同一数値が 2 回以上登場するマジックナンバー                          |
| `magicLiteralsInCalls[]`    | `Cells(3,5)` / `Range("A1")` 等の即値引数                          |
| `byRefAssignments[]`        | ByRef パラメーターへの代入（副作用の証拠）                           |
| `parameters[]`              | パラメーター名・型・ByVal 区別                                       |
| `returnType`                | Function の戻り値型（Sub/Property は null）                         |
| `ioSideEffectCount`         | `MsgBox` / `InputBox` / `Debug.Print` の呼び出し回数               |
| `referenceCount`            | 他プロシージャから呼ばれた回数（クロスファイル）                      |
| `hardcodedSheetCount`       | `Sheets("名前")` の固定シート名の種類数（凝集度指標）                |
| `hardcodedAddressCount`     | `Range("A1")` 等の固定アドレスの種類数（凝集度指標）                 |

#### ワークスペース単位（`WorkspaceReport`）

| フィールド              | 内容                                                                  |
|------------------------|-----------------------------------------------------------------------|
| `files[]`              | 各ファイルの `FileReport`                                              |
| `entryPointCandidates[]` | エントリポイント候補（他から呼ばれていない Public Sub 等）           |
| `deadCodeCandidates[]` | デッドコード候補（どこからも呼ばれていない Private Sub 等）            |
| `excelMockTargets[]`   | テスト時にモックが必要な Excel オブジェクト利用箇所                    |
| `callGraph[]`          | プロシージャ間の呼び出し関係（`from` → `to` のエッジ一覧）            |
| `duplicateBlocks[]`    | クローンコード（重複ブロック）の一覧                                   |

---

### 3-2. 分析の詳細

#### (A) 最大ネスト深度

`If` / `For` / `While` / `Do` / `Select Case` / `With` の各ブロックをネスト1段として再帰的に計測。

```
If                          → depth 1
  For                       → depth 2
    If                      → depth 3  ← maxNestDepth = 3
```

**活用**: 深度 ≥ 4 は「早期リターンへのリファクタリング」または「サブルーチン抽出」の候補。

#### (B) 連続代入ブロック（assignmentBlocks）

代入文 / Set 文 / Dim 宣言が 5 行以上連続する区間を検出。各ブロックに「形状」を付与する。

| 形状              | 意味                                              |
|------------------|---------------------------------------------------|
| `dim-decl`       | Dim 宣言のみ                                      |
| `mostly-dim-decl`| 大半が Dim 宣言（UDT や初期化フェーズ分離の候補）  |
| `range-read`     | Excel セルからの読み取りが主体                     |
| `range-write`    | Excel セルへの書き込みが主体                       |
| `var-init`       | リテラル値の変数初期化                             |
| `assign`         | 一般的な代入                                       |
| `set-obj`        | オブジェクト参照の Set 文                          |
| `mixed`          | 複合的                                            |

**活用**:
- `mostly-dim-decl` → 変数群を UDT（Type 宣言）にまとめる候補
- `range-read` + `range-write` が分離している → Excel アクセス層を関数に抽出できる
- 形状クラスタの違いがあれば → 責務（フェーズ）が混在しているサインで分割検討

#### (C) Excel アクセス検出（excelMockTargets）

以下のルートオブジェクトへのアクセスをすべてカウント・列挙する。

```
Sheets / Range / Cells / Application / ActiveSheet / ActiveWorkbook /
ActiveCell / ThisWorkbook / Workbook / Workbooks / Worksheet / Worksheets /
Columns / Rows / Selection
```

単体テストを書くには、これらへのアクセスを含むプロシージャを
「Excel 依存プロシージャ（テスト困難）」として分類し、モックオブジェクトか
Excel 非依存の純粋関数への分割を検討する。

#### (D) マジックナンバー / 固定アドレス

- **`repeatedNumericLiterals`**: 0, 1, -1 以外の数値が同一プロシージャ内で 2 回以上出現
- **`magicLiteralsInCalls`**: `Cells(3, 5)` / `Range("A1")` / `Sheets("Sheet1")` などの
  Range/Cells/Sheets 引数に直接書かれたリテラル

**活用**: 定数定義（`Const COL_AMOUNT = 5`）や設定オブジェクトへの移行候補。

#### (E) ByRef パラメーターへの代入

VBA のデフォルトは ByRef（参照渡し）。パラメーターへの代入は呼び出し元変数を書き換える
副作用になる。これを検出してリストアップする。

**活用**: ByRef 代入がある場合は「戻り値に変換」または「意図的な副作用であることをコメントで明示」する。

#### (F) IO 副作用カウント

`MsgBox` / `InputBox` / `Debug.Print` の呼び出し数を計測。
これらを含むプロシージャは単体テストが困難（UI 依存・出力依存）。

#### (G) 接頭辞クラスター検出（prefixClusters）

`COL_AMOUNT`, `COL_NAME`, `COL_DATE` のように共通接頭辞（`COL_`）を持つ定数・変数が
3 件以上ある場合、UDT（User Defined Type）または Enum への抽出候補として報告する。

検出条件:
- 識別子に `_` が含まれる
- `_` より前の部分が大文字英字のみ（例: `COL_`, `MAX_`, `IDX_`）
- 同じ接頭辞を持つ識別子が 3 件以上

#### (H) コールグラフ（callGraph）

各プロシージャが呼び出している識別子を収集し、同一ワークスペース内の定義プロシージャと
照合して `from → to` のエッジを構築する。

**活用**:
- `referenceCount == 0` かつ `Private` → デッドコード候補
- `referenceCount == 0` かつ `Public` → エントリポイント候補（またはデッドコード）

#### (I) 重複ブロック検出（duplicateBlocks）

ステートメントを正規化（変数名を `$ID`、文字列リテラルを `$STR` に置換）した上で
N-gram マッチングを行い、3 ステートメント以上かつ 2 箇所以上で繰り返されるコード片を検出。

```
// 例: 正規化後に一致する 2 箇所
$ID = $ID.Cells($ID, 1)
$ID = $ID.Cells($ID, 2)
$ID = $ID.Cells($ID, 3)
```

部分一致は長いパターンが優先され、短いパターンは除去（maximal match）。

---

### 3-3. 出力モード

| オプション    | 内容                                                              |
|--------------|-------------------------------------------------------------------|
| （なし）     | テキスト形式。人間が読みやすい整形済み出力                         |
| `--json`     | JSON 形式。`WorkspaceReport` 全体を stdout に出力                 |
| `--outline`  | AI 向けコンパクト要約。各プロシージャの 1 行サマリーのみ           |
| `--gen-test-dir <dir>` | 解析結果をもとにテストテンプレート（`.test.ts`）を自動生成 |
| `--gen-const <dir>`    | xl*/vb* 定数を `const.ts` として出力                       |

---

## 4. Java 実装の対応方針

### 4-1. パーサー（ANTLR4）の使い方

```java
// VBA ソースを読み込んでパースツリーを作成
CharStream input = CharStreams.fromPath(Path.of(filePath));
VBALexer lexer   = new VBALexer(input);
CommonTokenStream tokens = new CommonTokenStream(lexer);
VBAParser parser = new VBAParser(tokens);
VBAParser.StartRuleContext tree = parser.startRule();

// Visitor パターンで AST を走査
ProcedureCollector collector = new ProcedureCollector();
collector.visit(tree);
```

TypeScript 版は独自の AST を持つ（`type` フィールドを持つ JSON オブジェクト）が、
Java 版は ANTLR4 のパースツリー（`ParseTree`）をそのまま走査するため、
**各 Visitor が直接 `VBAParser.XxxContext` を扱う**設計になる。

### 4-2. ネスト深度の測定

```java
// VBAParserBaseVisitor を継承して実装
public class NestDepthVisitor extends VBAParserBaseVisitor<Integer> {
    private int current = 0;
    private int max = 0;

    @Override
    public Integer visitIfStmt(VBAParser.IfStmtContext ctx) {
        current++;
        max = Math.max(max, current);
        visitChildren(ctx);
        current--;
        return max;
    }
    // visitForNextStmt, visitDoLoopStmt, visitSelectCaseStmt, visitWithStmt も同様
}
```

### 4-3. Excel アクセス検出

ANTLR4 では `MemberAccessExpression`（`obj.prop`）や `ImplicitCallStmt_InStmt` のコンテキストを
走査し、ルートオブジェクト名が `EXCEL_ROOT_OBJECTS` セットに含まれているか確認する。

```java
private static final Set<String> EXCEL_ROOT_OBJECTS = Set.of(
    "sheets", "range", "cells", "application",
    "activesheet", "activeworkbook", "activecell",
    "thisworkbook", "workbook", "workbooks",
    "worksheet", "worksheets", "columns", "rows",
    "selection"
);

@Override
public Void visitMemberAccessExpression(VBAParser.MemberAccessExpressionContext ctx) {
    String root = ctx.lExpression().getText().toLowerCase();
    if (EXCEL_ROOT_OBJECTS.contains(root)) {
        excelAccessCount++;
        objectsUsed.add(normalize(root));
    }
    return visitChildren(ctx);
}
```

### 4-4. 連続代入ブロックの検出

ANTLR4 のパースツリーはリスト形式のステートメント列を持つため、
`block` コンテキストの子要素を順番に走査して連続する代入系ステートメントをグループ化する。

```java
List<StatementInfo> stmts = collectStatements(blockCtx);
int runStart = -1, runCount = 0;
for (StatementInfo s : stmts) {
    if (isAssignmentLike(s)) {
        if (runStart == -1) runStart = s.startLine;
        runCount++;
    } else {
        if (runCount >= 5) blocks.add(new AssignmentBlock(runStart, s.endLine - 1, runCount));
        runStart = -1; runCount = 0;
    }
}
```

### 4-5. JSON 出力

Gson を使って `WorkspaceReport` を JSON にシリアライズする。

```java
Gson gson = new GsonBuilder().setPrettyPrinting().create();
System.out.println(gson.toJson(workspaceReport));
```

### 4-6. TypeScript 版との主な相違点

| 項目                  | TypeScript 版                        | Java 版（ANTLR4）                          |
|----------------------|--------------------------------------|--------------------------------------------|
| パーサー              | 独自実装（`src/engine/parser.ts`）   | ANTLR4 生成パーサー（grammars-v4/vba）      |
| AST 表現              | JSON オブジェクト（`type` フィールド）| ANTLR4 `ParseTree` / `*Context` クラス      |
| 前処理                | `preprocess()` で `#If` を展開       | ANTLR4 文法で `#If` を字句的に処理 or 除去  |
| 型情報                | `ProcedureMetrics` インターフェース  | POJO（Plain Old Java Object）               |
| ファイル走査          | Node.js `fs` モジュール              | `java.nio.file.Files.walk()`               |
| テンプレート生成       | `--gen-test-dir` で `.test.ts` 出力  | Java 向けには JUnit5 テンプレートを生成     |

---

## 5. 実装優先順位の目安

1. **Phase 1（基本）**: プロシージャ一覧・行数・ネスト深度・Excel アクセス
   → リファクタリング候補の大まかな把握に十分
2. **Phase 2（ByRef / IO）**: ByRef 代入・IO 副作用カウント
   → テスト可能性の判定に必要
3. **Phase 3（詳細）**: 連続代入ブロック・マジックナンバー・接頭辞クラスター
   → リファクタリング作業の具体的な指示に使う
4. **Phase 4（クロスファイル）**: コールグラフ・デッドコード・重複ブロック
   → ワークスペース全体の品質評価に使う
