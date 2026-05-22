package javaanalyzer.spring;

public class EndpointInfo {
    public String httpMethod;    // GET / POST / PUT / DELETE / PATCH / ANY
    public String path;          // クラス basePath + メソッドパスの結合
    public String handlerClass;
    public String handlerMethod;
    public int line;
}
