package javaanalyzer.spring;

public class DiEdge {
    public String from;           // 依存元クラス名
    public String to;             // 注入される型名（インターフェース名）
    public String fieldName;      // フィールド名またはコンストラクタパラメーター名
    public String injectionType;  // field / constructor
}
