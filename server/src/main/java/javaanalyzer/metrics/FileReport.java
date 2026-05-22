package javaanalyzer.metrics;

import java.util.List;

public class FileReport {
    public String filePath;
    public int totalLines;
    public List<ClassReport> classes;
    public List<String> warnings;
}
