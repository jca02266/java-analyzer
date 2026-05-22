package javaanalyzer.analyzer;

import javaanalyzer.metrics.CallEdge;
import javaanalyzer.metrics.MethodMetrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全 CallEdge からコールグラフを構築し、
 * - referenceCount (各メソッドへの内部呼び出し数)
 * - deadCodeCandidates (private かつ referenceCount == 0)
 * - entryPointCandidates (public かつ referenceCount == 0)
 * を計算する。
 */
public class CallGraphBuilder {

    /** to → count */
    private final Map<String, Integer> refCount = new HashMap<>();

    public void addEdges(List<CallEdge> edges) {
        for (CallEdge e : edges) {
            refCount.merge(e.to, 1, Integer::sum);
        }
    }

    /** MethodMetrics の referenceCount を更新する */
    public void applyReferenceCounts(String className, List<MethodMetrics> methods) {
        for (MethodMetrics m : methods) {
            String key = className + "#" + m.name;
            m.referenceCount = refCount.getOrDefault(key, 0);
        }
    }

    /**
     * @param allMethods "ClassName#methodName" → MethodMetrics
     * @return [deadCandidates, entryPointCandidates]
     */
    public List<List<String>> classify(Map<String, MethodMetrics> allMethods) {
        List<String> dead   = new ArrayList<>();
        List<String> entry  = new ArrayList<>();

        for (Map.Entry<String, MethodMetrics> e : allMethods.entrySet()) {
            String key = e.getKey();
            MethodMetrics m = e.getValue();
            int refs = refCount.getOrDefault(key, 0);
            if (refs == 0) {
                if ("private".equals(m.scope)) {
                    dead.add(key);
                } else if ("public".equals(m.scope)) {
                    entry.add(key);
                }
            }
        }
        List<List<String>> result = new ArrayList<>();
        result.add(dead);
        result.add(entry);
        return result;
    }
}
