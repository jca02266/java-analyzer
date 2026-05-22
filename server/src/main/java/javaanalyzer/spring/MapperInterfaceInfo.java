package javaanalyzer.spring;

import java.util.List;
import java.util.Map;

/** @Mapper インターフェースの情報（MyBatis XML との対応付けに使用） */
public class MapperInterfaceInfo {
    public String className;
    public String filePath;                  // Java ファイルパス（定義ジャンプ用）
    public List<String> methods;
    public Map<String, Integer> methodLines; // メソッド名 → 行番号（1-based）
}
