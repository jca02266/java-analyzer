package javaanalyzer.mybatis;

import javaanalyzer.spring.MapperInterfaceInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Correlates Java @Mapper interfaces with MyBatis XML namespaces and statement IDs.
 * Attempts to match both fully qualified and simple names.
 */
public class MapperCorrelator {

    public MyBatisReport correlate(List<XmlMapperReport> xmlMappers,
                                   List<MapperInterfaceInfo> javaMappers) {
        MyBatisReport report = new MyBatisReport();
        report.xmlMappers      = xmlMappers != null ? xmlMappers : new ArrayList<>();
        report.unmappedMethods = new ArrayList<>();

        if (javaMappers == null || javaMappers.isEmpty()) return report;

        // namespace → XmlMapperReport map (both FQN and simple names)
        Map<String, XmlMapperReport> nsMap = new HashMap<>();
        for (XmlMapperReport xml : report.xmlMappers) {
            if (xml.namespace != null && !xml.namespace.isEmpty()) {
                nsMap.put(xml.namespace, xml);
                nsMap.put(simpleName(xml.namespace), xml);
            }
        }

        for (MapperInterfaceInfo javaMapper : javaMappers) {
            XmlMapperReport xmlMapper = nsMap.get(javaMapper.className);
            if (xmlMapper == null) {
                report.unmappedMethods.add(javaMapper.className + ": XML mapper not found");
                continue;
            }

            Set<String> xmlIds = xmlMapper.statements.stream()
                    .map(s -> s.id)
                    .collect(Collectors.toSet());

            if (javaMapper.methods != null) {
                for (String method : javaMapper.methods) {
                    if (!xmlIds.contains(method)) {
                        report.unmappedMethods.add(javaMapper.className + "#" + method);
                    }
                }
            }
        }

        return report;
    }

    private String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }
}
