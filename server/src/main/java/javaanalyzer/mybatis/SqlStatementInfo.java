package javaanalyzer.mybatis;

public class SqlStatementInfo {
    public String id;              // メソッド名に対応
    public String statementType;   // select / insert / update / delete
    public int    line;            // XML ファイル内の行番号（1-based、定義ジャンプ用）
    public String resultType;
    public String parameterType;

    // SQL 複雑度指標
    public int joinCount;          // JOIN 句の数
    public int subqueryCount;      // サブクエリ数（SELECT 出現数 - 1）
    public int unionCount;         // UNION の数

    // MyBatis 動的 SQL 複雑度
    public int ifTagCount;
    public int foreachTagCount;
    public int chooseTagCount;
    public int whereTagCount;
    public int setTagCount;
}
