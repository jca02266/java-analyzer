package javaanalyzer.spring;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spring Boot のアノテーションを静的解析する。
 * DI コンテナは起動せず、ソースコードのアノテーションのみを対象とする。
 */
public class SpringAnnotationVisitor {

    private static final Map<String, String> STEREOTYPE_MAP = new HashMap<>();
    private static final Set<String> MAPPING_ANNOTATIONS = new HashSet<>();
    private static final Map<String, String> HTTP_METHOD_MAP = new HashMap<>();

    static {
        STEREOTYPE_MAP.put("Component",       "component");
        STEREOTYPE_MAP.put("Service",         "service");
        STEREOTYPE_MAP.put("Repository",      "repository");
        STEREOTYPE_MAP.put("Controller",      "controller");
        STEREOTYPE_MAP.put("RestController",  "controller");
        STEREOTYPE_MAP.put("Configuration",   "configuration");

        MAPPING_ANNOTATIONS.addAll(Arrays.asList(
            "RequestMapping", "GetMapping", "PostMapping",
            "PutMapping", "DeleteMapping", "PatchMapping"
        ));

        HTTP_METHOD_MAP.put("GetMapping",    "GET");
        HTTP_METHOD_MAP.put("PostMapping",   "POST");
        HTTP_METHOD_MAP.put("PutMapping",    "PUT");
        HTTP_METHOD_MAP.put("DeleteMapping", "DELETE");
        HTTP_METHOD_MAP.put("PatchMapping",  "PATCH");
        HTTP_METHOD_MAP.put("RequestMapping","ANY");
    }

    private final List<EndpointInfo>       endpoints            = new ArrayList<>();
    private final List<BeanInfo>           beans                = new ArrayList<>();
    private final List<DiEdge>             diEdges              = new ArrayList<>();
    private final List<String>             transactionalMethods = new ArrayList<>();
    private final List<MapperInterfaceInfo> mapperInterfaces    = new ArrayList<>();

    public void analyze(CompilationUnit cu) {
        cu.getTypes().forEach(this::analyzeType);
    }

    public SpringReport buildReport() {
        SpringReport r = new SpringReport();
        r.endpoints            = endpoints;
        r.beans                = beans;
        r.diGraph              = diEdges;
        r.transactionalMethods = transactionalMethods;
        r.mapperInterfaces     = mapperInterfaces;
        return r;
    }

    // -------------------------------------------------------------------------

    private void analyzeType(TypeDeclaration<?> type) {
        if (!(type instanceof ClassOrInterfaceDeclaration)) return;
        ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) type;
        String className = cid.getNameAsString();

        // Bean 種別判定
        String beanType = detectBeanType(cid);
        if (beanType != null) {
            BeanInfo bean = new BeanInfo();
            bean.className = className;
            bean.beanType  = beanType;
            beans.add(bean);
        }

        // @Autowired / @Inject フィールドインジェクション
        for (FieldDeclaration field : cid.getFields()) {
            if (hasAnnotation(field, "Autowired") || hasAnnotation(field, "Inject")) {
                String typeName = field.getElementType().asString();
                field.getVariables().forEach(var -> {
                    DiEdge edge = new DiEdge();
                    edge.from          = className;
                    edge.to            = typeName;
                    edge.fieldName     = var.getNameAsString();
                    edge.injectionType = "field";
                    diEdges.add(edge);
                });
            }
        }

        // コンストラクタインジェクション
        // - @Autowired 付きコンストラクタ、または Spring Bean クラスで唯一のコンストラクタ（Spring 4.3+）
        List<ConstructorDeclaration> ctors = cid.getConstructors();
        for (ConstructorDeclaration ctor : ctors) {
            boolean explicit = hasAnnotation(ctor, "Autowired");
            boolean implicitSingle = beanType != null && ctors.size() == 1;
            if ((explicit || implicitSingle) && !ctor.getParameters().isEmpty()) {
                ctor.getParameters().forEach(param -> {
                    DiEdge edge = new DiEdge();
                    edge.from          = className;
                    edge.to            = param.getTypeAsString();
                    edge.fieldName     = param.getNameAsString();
                    edge.injectionType = "constructor";
                    diEdges.add(edge);
                });
            }
        }

        // エンドポイント（@Controller / @RestController）
        if (hasAnyAnnotation(cid, "Controller", "RestController")) {
            String basePath = classPath(cid);
            for (MethodDeclaration method : cid.getMethods()) {
                EndpointInfo ep = detectEndpoint(method, basePath, className);
                if (ep != null) endpoints.add(ep);
            }
        }

        // @Transactional メソッド
        for (MethodDeclaration method : cid.getMethods()) {
            if (hasAnnotation(method, "Transactional")) {
                transactionalMethods.add(className + "#" + method.getNameAsString());
            }
        }

        // @Configuration クラスの @Bean メソッド
        if (hasAnnotation(cid, "Configuration")) {
            for (MethodDeclaration method : cid.getMethods()) {
                if (hasAnnotation(method, "Bean")) {
                    BeanInfo bean = new BeanInfo();
                    bean.className = method.getTypeAsString();
                    bean.beanType  = "bean-method";
                    beans.add(bean);
                }
            }
        }

        // @Mapper インターフェース（MyBatis）
        if (cid.isInterface() && hasAnnotation(cid, "Mapper")) {
            MapperInterfaceInfo info = new MapperInterfaceInfo();
            info.className   = className;
            info.methods     = new ArrayList<>();
            info.methodLines = new java.util.HashMap<>();
            // CompilationUnit からファイルパスを取得
            cid.findCompilationUnit().ifPresent(cu ->
                cu.getStorage().ifPresent(s -> info.filePath = s.getPath().toAbsolutePath().toString())
            );
            cid.getMethods().forEach(m -> {
                info.methods.add(m.getNameAsString());
                m.getBegin().ifPresent(p -> info.methodLines.put(m.getNameAsString(), p.line));
            });
            mapperInterfaces.add(info);
        }

        // ネストクラスを再帰処理
        cid.getMembers().forEach(member -> {
            if (member instanceof ClassOrInterfaceDeclaration) {
                analyzeType((ClassOrInterfaceDeclaration) member);
            }
        });
    }

    private EndpointInfo detectEndpoint(MethodDeclaration method, String basePath, String className) {
        for (AnnotationExpr ann : method.getAnnotations()) {
            String annName = ann.getNameAsString();
            if (MAPPING_ANNOTATIONS.contains(annName)) {
                EndpointInfo ep = new EndpointInfo();
                ep.httpMethod    = HTTP_METHOD_MAP.getOrDefault(annName, "ANY");
                ep.path          = combinePaths(basePath, pathFromAnnotation(ann));
                ep.handlerClass  = className;
                ep.handlerMethod = method.getNameAsString();
                method.getBegin().ifPresent(p -> ep.line = p.line);
                return ep;
            }
        }
        return null;
    }

    /** クラスの @RequestMapping パスを取得 */
    private String classPath(ClassOrInterfaceDeclaration cid) {
        return cid.getAnnotations().stream()
                .filter(a -> a.getNameAsString().equals("RequestMapping"))
                .findFirst()
                .map(this::pathFromAnnotation)
                .orElse("");
    }

    private String detectBeanType(ClassOrInterfaceDeclaration cid) {
        for (AnnotationExpr ann : cid.getAnnotations()) {
            String t = STEREOTYPE_MAP.get(ann.getNameAsString());
            if (t != null) return t;
        }
        return null;
    }

    /** アノテーションから value / path の文字列を取得 */
    private String pathFromAnnotation(AnnotationExpr ann) {
        if (ann instanceof SingleMemberAnnotationExpr) {
            return stringValue(((SingleMemberAnnotationExpr) ann).getMemberValue());
        }
        if (ann instanceof NormalAnnotationExpr) {
            for (MemberValuePair pair : ((NormalAnnotationExpr) ann).getPairs()) {
                String key = pair.getNameAsString();
                if ("value".equals(key) || "path".equals(key)) {
                    return stringValue(pair.getValue());
                }
            }
        }
        return "";
    }

    private String stringValue(Expression expr) {
        if (expr instanceof StringLiteralExpr) {
            return ((StringLiteralExpr) expr).asString();
        }
        if (expr instanceof ArrayInitializerExpr) {
            return ((ArrayInitializerExpr) expr).getValues().stream()
                    .findFirst().map(this::stringValue).orElse("");
        }
        return expr.toString();
    }

    private String combinePaths(String base, String path) {
        if (base.isEmpty() && path.isEmpty()) return "/";
        if (base.isEmpty()) return path;
        if (path.isEmpty()) return base;
        return (base.endsWith("/") ? base : base + "/") + path.replaceFirst("^/", "");
    }

    private boolean hasAnnotation(NodeWithAnnotations<?> node, String name) {
        return node.getAnnotations().stream()
                .anyMatch(a -> a.getNameAsString().equals(name));
    }

    private boolean hasAnyAnnotation(NodeWithAnnotations<?> node, String... names) {
        Set<String> set = new HashSet<>(Arrays.asList(names));
        return node.getAnnotations().stream()
                .anyMatch(a -> set.contains(a.getNameAsString()));
    }
}
