package javaanalyzer.metrics;

import java.util.List;

public class PrefixCluster {
    public String prefix;            // 共通プレフィックス（例: "MAX_"）
    public List<String> identifiers; // 該当フィールド/定数名のリスト
    public String className;         // 所属クラス名
}
