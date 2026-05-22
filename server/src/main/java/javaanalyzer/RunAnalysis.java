package javaanalyzer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javaanalyzer.analyzer.JavaFileAnalyzer;
import javaanalyzer.analyzer.WorkspaceAnalyzer;
import javaanalyzer.metrics.FileReport;
import javaanalyzer.metrics.WorkspaceReport;

/**
 * VS Code を使わず CLI で解析結果を確認するためのスタンドアロン起動クラス。
 *
 * 使い方:
 *   java -cp server.jar javaanalyzer.RunAnalysis <path> [JAVA_8|JAVA_11|JAVA_17] [file|workspace]
 *
 * 例:
 *   java -cp server/target/java-analyzer-server.jar javaanalyzer.RunAnalysis ./sample JAVA_8 workspace
 *   java -cp server/target/java-analyzer-server.jar javaanalyzer.RunAnalysis ./sample/src/main/java/com/example/demo/service/UserService.java JAVA_8 file
 */
public class RunAnalysis {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: RunAnalysis <path> [JAVA_8|JAVA_11|JAVA_17] [file|workspace]");
            System.exit(1);
        }

        String path    = args[0];
        String level   = args.length > 1 ? args[1] : "JAVA_8";
        String mode    = args.length > 2 ? args[2] : "workspace";

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

        if ("file".equals(mode)) {
            JavaFileAnalyzer analyzer = new JavaFileAnalyzer(level);
            FileReport report = analyzer.analyze(path);
            System.out.println(gson.toJson(report));
        } else {
            WorkspaceAnalyzer analyzer = new WorkspaceAnalyzer(path, level);
            WorkspaceReport report = analyzer.analyze();
            System.out.println(gson.toJson(report));
        }
    }
}
