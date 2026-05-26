package javaanalyzer.diagnostics;

import com.github.javaparser.ast.CompilationUnit;
import javaanalyzer.visitors.EqualsTypeCheckVisitor;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts EqualsTypeCheckVisitor results into LSP Diagnostic objects.
 */
public class EqualsMismatchDetector {

    public List<Diagnostic> detect(CompilationUnit cu) {
        EqualsTypeCheckVisitor visitor = new EqualsTypeCheckVisitor();
        visitor.visit(cu, null);

        if (visitor.getMismatches().isEmpty()) {
            return Collections.emptyList();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (EqualsTypeCheckVisitor.Mismatch m : visitor.getMismatches()) {
            m.call.getBegin().ifPresent(begin ->
                m.call.getEnd().ifPresent(end -> {
                    Range range = new Range(
                        new Position(begin.line - 1, begin.column - 1),
                        new Position(end.line - 1, end.column)
                    );
                    String message = String.format(
                        "equals() comparing incompatible types: '%s' vs '%s' — always evaluates to false",
                        m.receiverType, m.argumentType);
                    Diagnostic d = new Diagnostic(range, message,
                        DiagnosticSeverity.Warning, "java-analyzer");
                    diagnostics.add(d);
                })
            );
        }
        return diagnostics;
    }
}
