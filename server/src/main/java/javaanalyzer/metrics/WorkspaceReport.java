package javaanalyzer.metrics;

import javaanalyzer.mybatis.MyBatisReport;
import javaanalyzer.spring.SpringReport;

import java.util.List;

public class WorkspaceReport {
    public List<FileReport> files;
    public SpringReport springReport;
    public MyBatisReport mybatisReport;
    public List<String> warnings;

    // Phase 5
    public List<CallEdge> callGraph;
    public List<String> deadCodeCandidates;    // "ClassName#method" (未呼び出し)
    public List<String> entryPointCandidates;  // "ClassName#method" (public で呼び出し元なし)
    public List<DuplicateBlock> duplicateBlocks;
}
