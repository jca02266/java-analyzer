package javaanalyzer.mybatis;

import java.util.List;

public class XmlMapperReport {
    public String filePath;
    public String namespace;             // XML の <mapper namespace="...">
    public List<SqlStatementInfo> statements;
}
