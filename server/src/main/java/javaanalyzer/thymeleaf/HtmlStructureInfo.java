package javaanalyzer.thymeleaf;

public class HtmlStructureInfo {
    public String templatePath;        // Template file path
    public int    maxDomDepth;         // Maximum DOM tree depth
    public int    formCount;           // Number of <form> elements
    public int    inlineScriptCount;   // Number of <script> elements
}
