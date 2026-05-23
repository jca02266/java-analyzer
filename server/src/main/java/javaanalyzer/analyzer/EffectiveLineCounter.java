package javaanalyzer.analyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.comments.Comment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Calculates effective (implementation) lines by excluding blank lines and comments.
 */
public class EffectiveLineCounter {

    /**
     * Count effective lines in the entire file (blank + comment lines removed).
     */
    public static int countFile(CompilationUnit cu, String filePath) {
        try {
            String content = Files.readString(Paths.get(filePath));
            String[] lines = content.split("\n", -1);
            int totalLines = lines.length;

            Set<Integer> commentLines = getCommentLineNumbers(cu);
            Set<Integer> blankLines = getBlankLineNumbers(lines);

            return totalLines - commentLines.size() - blankLines.size();
        } catch (IOException e) {
            return 0; // Fallback: if file read fails, return 0
        }
    }

    /**
     * Count effective lines for a method within a range.
     */
    public static int countMethod(int startLine, int endLine, String filePath) {
        try {
            String content = Files.readString(Paths.get(filePath));
            String[] lines = content.split("\n", -1);

            int effectiveCount = 0;
            for (int lineNum = startLine; lineNum <= endLine && lineNum <= lines.length; lineNum++) {
                String line = lines[lineNum - 1]; // Convert to 0-based
                if (!isBlankOrComment(line)) {
                    effectiveCount++;
                }
            }
            return effectiveCount;
        } catch (IOException e) {
            return Math.max(0, endLine - startLine + 1); // Fallback
        }
    }

    /**
     * Count effective lines for a callable (method/constructor) based on AST info.
     * Heuristic: exclude lines that are only { or } or contain only comments.
     */
    public static int countCallable(CallableDeclaration<?> callable) {
        int startLine = callable.getBegin().map(pos -> pos.line).orElse(1);
        int endLine = callable.getEnd().map(pos -> pos.line).orElse(startLine);

        // Heuristic: rough estimate based on lineCount minus typical braces/comments
        // Braces and pure comment lines are typically 1-2 lines
        int totalLines = Math.max(1, endLine - startLine + 1);
        int reduction = Math.max(0, Math.min(2, totalLines / 5)); // Reduce by ~20% at most
        return Math.max(1, totalLines - reduction);
    }

    /**
     * Get line numbers containing comments (both block and line comments).
     */
    private static Set<Integer> getCommentLineNumbers(CompilationUnit cu) {
        Set<Integer> commentLines = new HashSet<>();
        for (Comment comment : cu.getAllComments()) {
            int startLine = comment.getBegin().map(pos -> pos.line).orElse(0);
            int endLine = comment.getEnd().map(pos -> pos.line).orElse(startLine);
            for (int line = startLine; line <= endLine; line++) {
                commentLines.add(line);
            }
        }
        return commentLines;
    }

    /**
     * Get line numbers that are blank (only whitespace).
     */
    private static Set<Integer> getBlankLineNumbers(String[] lines) {
        Set<Integer> blankLines = new HashSet<>();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                blankLines.add(i + 1); // Convert to 1-based
            }
        }
        return blankLines;
    }

    /**
     * Simple check: line is blank or is a comment line (heuristic).
     */
    private static boolean isBlankOrComment(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return true;
        if (trimmed.startsWith("//")) return true;
        if (trimmed.startsWith("/*")) return true;
        if (trimmed.startsWith("*")) return true;
        if (trimmed.startsWith("*/")) return true;
        if (trimmed.startsWith("/**")) return true;
        return false;
    }
}
