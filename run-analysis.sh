#!/bin/bash
# java-analyzer 動作確認スクリプト
# 使い方:
#   ./run-analysis.sh                          # sample をワークスペース解析
#   ./run-analysis.sh workspace                # 同上
#   ./run-analysis.sh file <Javaファイルパス>   # 単一ファイル解析
#   ./run-analysis.sh workspace <任意パス>      # 任意ディレクトリを解析

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/server/target/java-analyzer-server-1.0-SNAPSHOT.jar"
LEVEL="${JAVA_LEVEL:-JAVA_8}"

# ビルドチェック
if [ ! -f "$JAR" ]; then
    echo "[build] server jar が見つかりません。ビルドします..."
    (cd "$SCRIPT_DIR/server" && mvn package -q)
fi

MODE="${1:-workspace}"

if [ "$MODE" = "file" ]; then
    TARGET="${2:-$SCRIPT_DIR/sample/src/main/java/com/example/demo/service/UserService.java}"
    echo "[analyze] ファイル解析: $TARGET"
    java -cp "$JAR" javaanalyzer.RunAnalysis "$TARGET" "$LEVEL" file | python3 -m json.tool 2>/dev/null \
        || java -cp "$JAR" javaanalyzer.RunAnalysis "$TARGET" "$LEVEL" file
else
    TARGET="${2:-$SCRIPT_DIR/sample}"
    echo "[analyze] ワークスペース解析: $TARGET"
    java -cp "$JAR" javaanalyzer.RunAnalysis "$TARGET" "$LEVEL" workspace | python3 -m json.tool 2>/dev/null \
        || java -cp "$JAR" javaanalyzer.RunAnalysis "$TARGET" "$LEVEL" workspace
fi
