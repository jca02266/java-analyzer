package javaanalyzer.metrics;

import java.util.List;

public class ClassReport {
    public String className;
    public String kind;    // class / interface / enum / annotation
    public String scope;   // public / package-private
    public int startLine;
    public int endLine;
    public List<MethodMetrics> methods;

    // Phase 5
    public List<PrefixCluster> prefixClusters;
}
