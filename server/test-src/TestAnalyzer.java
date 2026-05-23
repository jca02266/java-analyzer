import javaanalyzer.analyzer.WorkspaceAnalyzer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TestAnalyzer {
    public static void main(String[] args) throws Exception {
        String samplePath = "/Users/koji/src/github.com/jca02266/java-analyzer/sample";
        WorkspaceAnalyzer analyzer = new WorkspaceAnalyzer("JAVA_8", samplePath);
        var report = analyzer.analyze();
        Gson gson = new GsonBuilder().setPrettyPrinter().create();
        System.out.println(gson.toJson(report));
    }
}
