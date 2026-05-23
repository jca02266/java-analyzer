package javaanalyzer.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import javaanalyzer.metrics.AssignmentBlock;
import javaanalyzer.metrics.ClassReport;
import javaanalyzer.metrics.FileReport;
import javaanalyzer.metrics.MethodMetrics;
import javaanalyzer.metrics.ParameterInfo;
import javaanalyzer.visitors.AssignmentBlockVisitor;
import javaanalyzer.visitors.EqualsTypeCheckVisitor;
import javaanalyzer.visitors.ExternalCallVisitor;
import javaanalyzer.visitors.LambdaStreamVisitor;
import javaanalyzer.visitors.MagicNumberVisitor;
import javaanalyzer.visitors.NestDepthVisitor;
import javaanalyzer.visitors.NullSafetyVisitor;
import javaanalyzer.visitors.PrefixClusterDetector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaFileAnalyzer {

    private final ParserConfiguration.LanguageLevel languageLevel;
    private final String workspacePath;  // null の場合 SymbolSolver 無効

    public JavaFileAnalyzer(String languageLevel) {
        this(languageLevel, null);
    }

    public JavaFileAnalyzer(String languageLevel, String workspacePath) {
        this.languageLevel = resolveLanguageLevel(languageLevel);
        this.workspacePath = workspacePath;
    }

    public JavaParser createParser() {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(languageLevel);
        if (workspacePath != null) {
            config.setSymbolResolver(SymbolSolverConfig.create(workspacePath));
        }
        return new JavaParser(config);
    }

    public FileReport analyze(String filePath) {
        FileReport report = new FileReport();
        report.filePath = filePath;
        report.classes  = new ArrayList<>();
        report.warnings = new ArrayList<>();

        ParseResult<CompilationUnit> result;
        try {
            result = createParser().parse(new File(filePath));
        } catch (Exception e) {
            report.warnings.add("Parse error: " + e.getMessage());
            return report;
        }

        result.getProblems().forEach(p -> report.warnings.add(p.toString()));
        result.getResult().ifPresent(cu -> {
            populateReport(report, cu);
            report.effectiveLines = EffectiveLineCounter.countFile(cu, filePath);
        });
        return report;
    }

    /** 既にパース済みの CompilationUnit を受け取って FileReport を生成する（WorkspaceAnalyzer 用） */
    public FileReport analyze(CompilationUnit cu, String filePath) {
        FileReport report = new FileReport();
        report.filePath = filePath;
        report.classes  = new ArrayList<>();
        report.warnings = new ArrayList<>();
        populateReport(report, cu);
        report.effectiveLines = EffectiveLineCounter.countFile(cu, filePath);
        return report;
    }

    private void populateReport(FileReport report, CompilationUnit cu) {
        cu.getEnd().ifPresent(pos -> report.totalLines = pos.line);
        cu.getTypes().forEach(type -> report.classes.add(analyzeType(type)));
    }

    private ClassReport analyzeType(TypeDeclaration<?> type) {
        ClassReport cr = new ClassReport();
        cr.className = type.getNameAsString();
        cr.kind  = typeKind(type);
        cr.scope = hasModifier(type, Modifier.Keyword.PUBLIC) ? "public" : "package-private";
        type.getBegin().ifPresent(p -> cr.startLine = p.line);
        type.getEnd().ifPresent(p -> cr.endLine = p.line);
        cr.methods = new ArrayList<>();

        if (type instanceof ClassOrInterfaceDeclaration) {
            for (ConstructorDeclaration ctor : ((ClassOrInterfaceDeclaration) type).getConstructors()) {
                cr.methods.add(analyzeCallable(ctor, "constructor"));
            }
        }

        for (MethodDeclaration method : type.getMethods()) {
            cr.methods.add(analyzeCallable(method, "method"));
        }

        PrefixClusterDetector pcd = new PrefixClusterDetector();
        cr.prefixClusters = pcd.detect(type);

        return cr;
    }

    private MethodMetrics analyzeCallable(CallableDeclaration<?> callable, String kind) {
        MethodMetrics m = new MethodMetrics();
        m.name  = callable.getNameAsString();
        m.kind  = kind;
        m.scope = scope(callable);
        callable.getBegin().ifPresent(p -> m.startLine = p.line);
        callable.getEnd().ifPresent(p -> m.endLine = p.line);
        m.lineCount = m.endLine - m.startLine + 1;
        m.effectiveLineCount = EffectiveLineCounter.countCallable(callable);

        if ("method".equals(kind)) {
            m.returnType = ((MethodDeclaration) callable).getTypeAsString();
        }

        m.parameters = new ArrayList<>();
        callable.getParameters().forEach(param ->
                m.parameters.add(new ParameterInfo(param.getNameAsString(), param.getTypeAsString())));

        m.localDeclCount = countLocalDecls(callable);

        // ネスト深度
        NestDepthVisitor nestVisitor = new NestDepthVisitor();
        callable.accept(nestVisitor, null);
        m.maxNestDepth = nestVisitor.getMax();

        // ラムダ / Stream / メソッド参照
        LambdaStreamVisitor lambdaVisitor = new LambdaStreamVisitor();
        callable.accept(lambdaVisitor, null);
        m.lambdaCount          = lambdaVisitor.getLambdaCount();
        m.maxMethodChainDepth  = lambdaVisitor.getMaxMethodChainDepth();
        m.methodReferenceCount = lambdaVisitor.getMethodReferenceCount();

        // 外部依存 / IO 副作用 / Optional.get()
        ExternalCallVisitor extVisitor = new ExternalCallVisitor();
        callable.accept(extVisitor, null);
        m.externalCallCount      = extVisitor.getExternalCallCount();
        m.externalCallCategories = extVisitor.getCategories();
        m.ioSideEffectCount      = extVisitor.getIoSideEffectCount();
        m.optionalDirectGet      = extVisitor.getOptionalDirectGet();

        // equals() 型安全チェック
        EqualsTypeCheckVisitor equalsVisitor = new EqualsTypeCheckVisitor();
        callable.accept(equalsVisitor, null);
        m.equalsTypeMismatchCount = equalsVisitor.getEqualsTypeMismatchCount();

        // Null 安全チェック
        NullSafetyVisitor nullVisitor = new NullSafetyVisitor();
        callable.accept(nullVisitor, null);
        m.nullSafetyIssueCount = nullVisitor.getNullSafetyIssueCount();

        // マジックナンバー
        Map<String, Integer> literalCounts = new HashMap<>();
        MagicNumberVisitor magicVisitor = new MagicNumberVisitor();
        callable.accept(magicVisitor, literalCounts);
        m.repeatedNumericLiterals = MagicNumberVisitor.repeated(literalCounts);

        // 連続代入ブロック
        AssignmentBlockVisitor abVisitor = new AssignmentBlockVisitor();
        List<AssignmentBlock> blocks;
        if ("method".equals(kind)) {
            blocks = abVisitor.analyze((MethodDeclaration) callable);
        } else {
            blocks = abVisitor.analyze((ConstructorDeclaration) callable);
        }
        m.assignmentBlocks = blocks;

        return m;
    }

    private int countLocalDecls(CallableDeclaration<?> callable) {
        int[] count = {0};
        callable.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(VariableDeclarationExpr n, Void arg) {
                count[0] += n.getVariables().size();
                super.visit(n, arg);
            }
        }, null);
        return count[0];
    }

    private String scope(CallableDeclaration<?> callable) {
        if (hasModifier(callable, Modifier.Keyword.PUBLIC))    return "public";
        if (hasModifier(callable, Modifier.Keyword.PRIVATE))   return "private";
        if (hasModifier(callable, Modifier.Keyword.PROTECTED)) return "protected";
        return "package";
    }

    private boolean hasModifier(com.github.javaparser.ast.nodeTypes.NodeWithModifiers<?> node,
                                 Modifier.Keyword keyword) {
        return node.getModifiers().stream().anyMatch(mod -> mod.getKeyword() == keyword);
    }

    private String typeKind(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration) {
            return ((ClassOrInterfaceDeclaration) type).isInterface() ? "interface" : "class";
        }
        if (type instanceof com.github.javaparser.ast.body.EnumDeclaration)      return "enum";
        if (type instanceof com.github.javaparser.ast.body.AnnotationDeclaration) return "annotation";
        return "class";
    }

    private ParserConfiguration.LanguageLevel resolveLanguageLevel(String level) {
        switch (level) {
            case "JAVA_11": return ParserConfiguration.LanguageLevel.JAVA_11;
            case "JAVA_17": return ParserConfiguration.LanguageLevel.JAVA_17;
            default:        return ParserConfiguration.LanguageLevel.JAVA_8;
        }
    }
}
