package javaanalyzer.visitors;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detects type mismatches in equals() / equalsIgnoreCase() / Objects.equals() calls.
 * Type mismatches always evaluate to false, indicating a likely bug.
 */
public class EqualsTypeCheckVisitor extends VoidVisitorAdapter<Void> {

    /** Holds position and type information for a detected mismatch. */
    public static class Mismatch {
        public final MethodCallExpr call;
        public final String receiverType;
        public final String argumentType;

        Mismatch(MethodCallExpr call, String receiverType, String argumentType) {
            this.call = call;
            this.receiverType = receiverType;
            this.argumentType = argumentType;
        }
    }

    private int equalsTypeMismatchCount = 0;
    private final List<Mismatch> mismatches = new ArrayList<>();

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        super.visit(n, arg);

        String methodName = n.getNameAsString();

        // Check equals() / equalsIgnoreCase() on an object
        if (("equals".equals(methodName) || "equalsIgnoreCase".equals(methodName)) && n.getArguments().size() == 1) {
            checkTypeMatch(n);
        }

        // Check Objects.equals(a, b)
        if ("equals".equals(methodName) && n.getArguments().size() == 2) {
            if (n.getScope().isPresent() && "Objects".equals(n.getScope().get().toString())) {
                checkObjectsEquals(n);
            }
        }
    }

    /**
     * Check a.equals(b) — compare types of scope and argument.
     */
    private void checkTypeMatch(MethodCallExpr n) {
        try {
            // Get the scope type (e.g., type of 'a' in a.equals(b))
            if (!n.getScope().isPresent()) return;

            ResolvedType scopeType = n.getScope().get().calculateResolvedType();
            ResolvedType argType = n.getArguments().get(0).calculateResolvedType();

            if (isTypeMismatch(scopeType, argType)) {
                equalsTypeMismatchCount++;
                mismatches.add(new Mismatch(n, simpleName(scopeType), simpleName(argType)));
            }
        } catch (Exception ignored) {
            // SymbolSolver may fail; ignore and continue
        }
    }

    /**
     * Check Objects.equals(a, b) — compare types of two arguments.
     */
    private void checkObjectsEquals(MethodCallExpr n) {
        try {
            if (n.getArguments().size() < 2) return;

            ResolvedType arg1Type = n.getArguments().get(0).calculateResolvedType();
            ResolvedType arg2Type = n.getArguments().get(1).calculateResolvedType();

            if (isTypeMismatch(arg1Type, arg2Type)) {
                equalsTypeMismatchCount++;
                mismatches.add(new Mismatch(n, simpleName(arg1Type), simpleName(arg2Type)));
            }
        } catch (Exception ignored) {
            // SymbolSolver may fail; ignore and continue
        }
    }

    /**
     * Determine if two types are genuinely mismatched (not just autoboxing differences).
     */
    private boolean isTypeMismatch(ResolvedType type1, ResolvedType type2) {
        // Both are same type
        if (type1.equals(type2)) return false;

        // One is primitive, other is wrapper — autoboxing, no mismatch
        if (isPrimitive(type1) && isWrapper(type2, type1)) return false;
        if (isWrapper(type1, type2) && isPrimitive(type2)) return false;

        // Both are classes/interfaces — check assignability
        try {
            if (type1.isReferenceType() && type2.isReferenceType()) {
                // If one is assignable from the other, not a real mismatch
                if (type1.asReferenceType().isAssignableBy(type2.asReferenceType())) return false;
                if (type2.asReferenceType().isAssignableBy(type1.asReferenceType())) return false;
            }
        } catch (Exception ignored) {
            // Ignore if comparison fails
        }

        // Both are primitives but different — true mismatch
        if (isPrimitive(type1) && isPrimitive(type2)) {
            return !type1.equals(type2);
        }

        // Primitive vs class (other than wrapper) — true mismatch
        if (isPrimitive(type1) || isPrimitive(type2)) {
            return true;
        }

        // Different classes with no inheritance relation — likely mismatch
        return !type1.describe().equals(type2.describe());
    }

    private boolean isPrimitive(ResolvedType type) {
        return type.isPrimitive();
    }

    private boolean isWrapper(ResolvedType wrapperCandidate, ResolvedType primitiveType) {
        if (!primitiveType.isPrimitive()) return false;

        String primitiveName = primitiveType.describe();
        String wrapperName = wrapperCandidate.describe();

        return (primitiveName.equals("int") && wrapperName.equals("java.lang.Integer"))
                || (primitiveName.equals("long") && wrapperName.equals("java.lang.Long"))
                || (primitiveName.equals("double") && wrapperName.equals("java.lang.Double"))
                || (primitiveName.equals("float") && wrapperName.equals("java.lang.Float"))
                || (primitiveName.equals("boolean") && wrapperName.equals("java.lang.Boolean"))
                || (primitiveName.equals("byte") && wrapperName.equals("java.lang.Byte"))
                || (primitiveName.equals("short") && wrapperName.equals("java.lang.Short"))
                || (primitiveName.equals("char") && wrapperName.equals("java.lang.Character"));
    }

    public int getEqualsTypeMismatchCount() {
        return equalsTypeMismatchCount;
    }

    public List<Mismatch> getMismatches() {
        return Collections.unmodifiableList(mismatches);
    }

    private String simpleName(ResolvedType t) {
        String desc = t.describe();
        int dot = desc.lastIndexOf('.');
        return dot >= 0 ? desc.substring(dot + 1) : desc;
    }
}
