package javaanalyzer.metrics;

public class CallEdge {
    public String from;      // "ClassName#methodName"
    public String to;        // "ClassName#methodName"
    public boolean resolved; // シンボルソルバで解決できたか
}
