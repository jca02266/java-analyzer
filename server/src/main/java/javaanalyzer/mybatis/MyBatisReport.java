package javaanalyzer.mybatis;

import java.util.List;

public class MyBatisReport {
    public List<XmlMapperReport> xmlMappers;
    public List<String> unmappedMethods;  // XML に対応がない Mapper メソッド（"Class#method" 形式）
}
