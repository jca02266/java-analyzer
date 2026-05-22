package javaanalyzer.spring;

import java.util.List;

public class SpringReport {
    public List<EndpointInfo> endpoints;
    public List<BeanInfo> beans;
    public List<DiEdge> diGraph;
    public List<String> transactionalMethods;   // "ClassName#methodName" 形式
    public List<MapperInterfaceInfo> mapperInterfaces;  // @Mapper インターフェース一覧
}
