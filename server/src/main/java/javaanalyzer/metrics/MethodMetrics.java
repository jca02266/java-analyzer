package javaanalyzer.metrics;

import java.util.List;

public class MethodMetrics {
    public String name;
    public String kind;                          // method / constructor
    public String scope;                         // public / private / protected / package
    public int startLine;
    public int endLine;
    public int lineCount;
    public int effectiveLineCount;  // Excluding blank lines and comments
    public int maxNestDepth;
    public int localDeclCount;
    public int lambdaCount;
    public int maxMethodChainDepth;              // Stream チェーン深度の目安
    public int methodReferenceCount;
    public int optionalDirectGet;                // Optional.get() 直呼び回数
    public int equalsTypeMismatchCount;           // Type mismatches in equals() calls
    public int nullSafetyIssueCount;              // Null literal passed to methods
    public int externalCallCount;                // JDBC / HTTP / File I/O 呼び出し数
    public List<String> externalCallCategories;  // 検出カテゴリ（JDBC / HTTP / FILE_IO 等）
    public int ioSideEffectCount;                // System.out / Logger / Scanner
    public List<String> repeatedNumericLiterals; // 2 回以上出現するマジックナンバー
    public List<AssignmentBlock> assignmentBlocks; // 5 行以上の連続代入ブロック
    public String returnType;                    // constructor は null
    public List<ParameterInfo> parameters;

    // Phase 5
    public int referenceCount;                   // このメソッドへの内部呼び出し数
}
