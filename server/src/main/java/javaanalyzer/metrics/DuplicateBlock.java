package javaanalyzer.metrics;

import java.util.List;

public class DuplicateBlock {
    public int stmtCount;        // 重複ステートメント数
    public String preview;       // 先頭ステートメントの文字列（最大80文字）
    public List<String> locations; // "ClassName#method:startLine" のリスト
}
