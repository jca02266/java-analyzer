package javaanalyzer.analyzer;

import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * JavaSymbolSolver のセットアップヘルパー。
 * ReflectionTypeSolver（JDK）＋ JavaParserTypeSolver（プロジェクトソース）を組み合わせる。
 * ソースディレクトリが存在しない場合は JDK のみで動作する。
 */
public class SymbolSolverConfig {

    private static final List<String> SOURCE_CANDIDATES = Arrays.asList(
            "src/main/java",
            "src/main/kotlin",
            "src"
    );

    public static JavaSymbolSolver create(String workspacePath) {
        CombinedTypeSolver solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());

        for (String candidate : SOURCE_CANDIDATES) {
            File dir = new File(workspacePath, candidate);
            if (dir.isDirectory()) {
                try {
                    solver.add(new JavaParserTypeSolver(dir));
                } catch (Exception ignored) {
                    // ディレクトリが読めない場合は無視
                }
            }
        }

        return new JavaSymbolSolver(solver);
    }
}
