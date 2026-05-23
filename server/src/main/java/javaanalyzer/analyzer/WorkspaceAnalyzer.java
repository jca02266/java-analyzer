package javaanalyzer.analyzer;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import javaanalyzer.metrics.CallEdge;
import javaanalyzer.metrics.ClassReport;
import javaanalyzer.metrics.FileReport;
import javaanalyzer.metrics.MethodMetrics;
import javaanalyzer.metrics.WorkspaceReport;
import javaanalyzer.mybatis.MapperCorrelator;
import javaanalyzer.mybatis.MyBatisXmlParser;
import javaanalyzer.mybatis.XmlMapperReport;
import javaanalyzer.spring.SpringAnnotationVisitor;
import javaanalyzer.spring.SpringReport;
import javaanalyzer.thymeleaf.JsAnalyzer;
import javaanalyzer.thymeleaf.ThymeleafParser;
import javaanalyzer.thymeleaf.ViewLayerCorrelator;
import javaanalyzer.visitors.CallCollector;
import javaanalyzer.visitors.DuplicateBlockDetector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class WorkspaceAnalyzer {

    private final String workspacePath;
    private final JavaFileAnalyzer fileAnalyzer;

    public WorkspaceAnalyzer(String workspacePath, String languageLevel) {
        this.workspacePath = workspacePath;
        this.fileAnalyzer  = new JavaFileAnalyzer(languageLevel, workspacePath);
    }

    public WorkspaceReport analyze() {
        WorkspaceReport report = new WorkspaceReport();
        report.files    = new ArrayList<>();
        report.warnings = new ArrayList<>();

        SpringAnnotationVisitor springVisitor = new SpringAnnotationVisitor();
        CallCollector callCollector = new CallCollector();
        DuplicateBlockDetector dupDetector = new DuplicateBlockDetector();
        List<Path> javaFiles = collectJavaFiles(report.warnings);

        List<CallEdge> allEdges = new ArrayList<>();

        for (Path path : javaFiles) {
            String filePath = path.toString();
            ParseResult<CompilationUnit> result;
            try {
                result = fileAnalyzer.createParser().parse(new File(filePath));
            } catch (Exception e) {
                report.warnings.add("Parse error [" + filePath + "]: " + e.getMessage());
                continue;
            }

            result.getProblems().forEach(p ->
                    report.warnings.add("[" + filePath + "] " + p.toString()));

            result.getResult().ifPresent(cu -> {
                // ファイル解析（メトリクス）
                FileReport fileReport = fileAnalyzer.analyze(cu, filePath);
                report.files.add(fileReport);

                // Spring アノテーション解析（同一 AST を再利用）
                springVisitor.analyze(cu);

                // Phase 5: コール収集・重複検出
                allEdges.addAll(callCollector.collect(cu));
                cu.getTypes().forEach(dupDetector::index);
            });
        }

        SpringReport springReport = springVisitor.buildReport();
        report.springReport = springReport;

        // MyBatis XML 解析
        MyBatisXmlParser xmlParser = new MyBatisXmlParser();
        List<XmlMapperReport> xmlMappers = xmlParser.parseWorkspace(workspacePath, report.warnings);

        // Java Mapper ↔ XML 対応付け
        MapperCorrelator correlator = new MapperCorrelator();
        report.mybatisReport = correlator.correlate(xmlMappers, springReport.mapperInterfaces);

        // Thymeleaf / HTML / JavaScript 解析
        ThymeleafParser thymeleafParser = new ThymeleafParser();
        JsAnalyzer jsAnalyzer = new JsAnalyzer();
        ViewLayerCorrelator viewCorrelator = new ViewLayerCorrelator();
        report.thymeleafReport = viewCorrelator.correlate(
            thymeleafParser.parseWorkspace(workspacePath, report.warnings),
            jsAnalyzer.analyzeWorkspace(workspacePath, report.warnings),
            springReport.endpoints
        );

        // Phase 5: コールグラフ構築
        CallGraphBuilder cgBuilder = new CallGraphBuilder();
        cgBuilder.addEdges(allEdges);

        // referenceCount を各 MethodMetrics に反映 & 全メソッドマップ構築
        Map<String, MethodMetrics> allMethods = new HashMap<>();
        for (FileReport fr : report.files) {
            for (ClassReport cr : fr.classes != null ? fr.classes : new ArrayList<ClassReport>()) {
                cgBuilder.applyReferenceCounts(cr.className, cr.methods != null ? cr.methods : new ArrayList<>());
                for (MethodMetrics m : cr.methods != null ? cr.methods : new ArrayList<MethodMetrics>()) {
                    allMethods.put(cr.className + "#" + m.name, m);
                }
            }
        }

        List<List<String>> classified = cgBuilder.classify(allMethods);
        report.callGraph             = allEdges;
        report.deadCodeCandidates    = classified.get(0);
        report.entryPointCandidates  = classified.get(1);
        report.duplicateBlocks       = dupDetector.getResults();

        return report;
    }

    private List<Path> collectJavaFiles(List<String> warnings) {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(Paths.get(workspacePath))) {
            stream.filter(p -> p.toString().endsWith(".java"))
                  .filter(p -> !isTestPath(p))
                  .forEach(result::add);
        } catch (IOException e) {
            warnings.add("Walk error: " + e.getMessage());
        }
        return result;
    }

    private boolean isTestPath(Path p) {
        String s = p.toString();
        return s.contains("/test/") || s.contains("\\test\\");
    }
}
