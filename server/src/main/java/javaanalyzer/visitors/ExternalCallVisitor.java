package javaanalyzer.visitors;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 外部依存呼び出し（JDBC / HTTP / File I/O）と IO 副作用（System.out / Logger / Scanner）を検出する。
 * SymbolSolver による型解決を優先し、失敗した場合はメソッド名・スコープ名のヒューリスティックにフォールバックする。
 */
public class ExternalCallVisitor extends VoidVisitorAdapter<Void> {

    // 型名プレフィックス → カテゴリ（SymbolSolver 使用時）
    private static final List<String[]> TYPE_PREFIXES = Arrays.asList(
            new String[]{"java.sql.",              "JDBC"},
            new String[]{"javax.sql.",             "JDBC"},
            new String[]{"java.io.File",           "FILE_IO"},
            new String[]{"java.io.Buffered",       "FILE_IO"},
            new String[]{"java.io.PrintStream",    "SYSTEM_IO"},
            new String[]{"java.nio.file.",         "FILE_IO"},
            new String[]{"java.net.http.",         "HTTP"},
            new String[]{"java.util.Scanner",      "SCANNER"},
            new String[]{"org.slf4j.Logger",       "LOGGER"},
            new String[]{"java.util.logging.Logger","LOGGER"},
            new String[]{"org.apache.logging.log4j","LOGGER"}
    );

    // ヒューリスティック：メソッド名 → カテゴリ
    private static final Set<String> JDBC_METHODS = new HashSet<>(Arrays.asList(
            "getConnection", "prepareStatement", "prepareCall",
            "createStatement", "executeQuery", "executeUpdate", "execute"
    ));
    private static final Set<String> HTTP_METHODS = new HashSet<>(Arrays.asList(
            "exchange", "getForEntity", "getForObject",
            "postForEntity", "postForObject", "put", "send"
    ));
    private static final Set<String> FILE_IO_CTORS = new HashSet<>(Arrays.asList(
            "FileInputStream", "FileOutputStream", "FileReader", "FileWriter",
            "BufferedReader", "BufferedWriter", "PrintWriter", "RandomAccessFile"
    ));
    private static final Set<String> IO_SIDE_METHODS = new HashSet<>(Arrays.asList(
            "println", "print", "printf", "format"
    ));
    private static final Set<String> LOGGER_SCOPE_NAMES = new HashSet<>(Arrays.asList(
            "logger", "log", "LOG", "LOGGER"
    ));
    private static final Set<String> LOGGER_METHODS = new HashSet<>(Arrays.asList(
            "info", "debug", "warn", "error", "trace", "fatal"
    ));

    private int externalCallCount = 0;
    private int ioSideEffectCount = 0;
    private int optionalDirectGet = 0;
    private final Set<String> categories = new HashSet<>();

    public int getExternalCallCount()        { return externalCallCount; }
    public int getIoSideEffectCount()        { return ioSideEffectCount; }
    public int getOptionalDirectGet()        { return optionalDirectGet; }
    public List<String> getCategories()      { return new ArrayList<>(categories); }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        String method    = n.getNameAsString();
        String scopeStr  = n.getScope().map(Expression::toString).orElse("");

        // SymbolSolver による型解決を試みる
        boolean resolved = tryResolveType(n);

        if (!resolved) {
            // フォールバック: ヒューリスティック
            if ("System.out".equals(scopeStr) || "System.err".equals(scopeStr)) {
                if (IO_SIDE_METHODS.contains(method)) { ioSideEffectCount++; categories.add("SYSTEM_IO"); }
            } else if (LOGGER_SCOPE_NAMES.contains(scopeStr) && LOGGER_METHODS.contains(method)) {
                ioSideEffectCount++; categories.add("LOGGER");
            } else if (JDBC_METHODS.contains(method)) {
                externalCallCount++; categories.add("JDBC");
            } else if (HTTP_METHODS.contains(method)) {
                externalCallCount++; categories.add("HTTP");
            } else if ("Files".equals(scopeStr) && isFileIoMethod(method)) {
                externalCallCount++; categories.add("FILE_IO");
            }
        }

        // Optional.get() 直呼びのヒューリスティック検出（型解決補助）
        if ("get".equals(method) && n.getArguments().isEmpty()) {
            n.getScope().ifPresent(scope -> {
                String s = scope.toString().toLowerCase();
                boolean looksOptional = s.contains("opt") || scope instanceof MethodCallExpr;
                if (looksOptional) optionalDirectGet++;
            });
        }

        super.visit(n, arg);
    }

    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        String type = n.getTypeAsString();
        if (FILE_IO_CTORS.contains(type)) {
            externalCallCount++; categories.add("FILE_IO");
        }
        if ("Scanner".equals(type)) {
            ioSideEffectCount++; categories.add("SCANNER");
        }
        super.visit(n, arg);
    }

    /** SymbolSolver で型を解決してカテゴリを付与する。成功した場合 true を返す */
    private boolean tryResolveType(MethodCallExpr n) {
        return n.getScope().map(scope -> {
            try {
                String typeName = scope.calculateResolvedType().describe();
                return classifyByTypeName(n.getNameAsString(), typeName);
            } catch (Exception ignored) {
                return false;
            }
        }).orElse(false);
    }

    private boolean classifyByTypeName(String method, String typeName) {
        for (String[] entry : TYPE_PREFIXES) {
            if (typeName.startsWith(entry[0])) {
                String cat = entry[1];
                switch (cat) {
                    case "SYSTEM_IO":
                    case "LOGGER":
                    case "SCANNER":
                        ioSideEffectCount++; categories.add(cat); return true;
                    default:
                        externalCallCount++; categories.add(cat); return true;
                }
            }
        }
        // Spring HTTP クライアント（型名に RestTemplate / WebClient / HttpClient を含む）
        if (typeName.contains("RestTemplate") || typeName.contains("WebClient")
                || typeName.contains("FeignClient")) {
            externalCallCount++; categories.add("HTTP"); return true;
        }
        return false;
    }

    private boolean isFileIoMethod(String method) {
        return "readAllBytes".equals(method) || "readAllLines".equals(method)
                || "write".equals(method)    || "copy".equals(method)
                || "move".equals(method)     || "delete".equals(method)
                || "newInputStream".equals(method) || "newOutputStream".equals(method);
    }
}
