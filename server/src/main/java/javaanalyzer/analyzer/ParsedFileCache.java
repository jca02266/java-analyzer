package javaanalyzer.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CompilationUnit をファイルパスをキーにキャッシュする。
 * ファイルの最終更新時刻が変わっていれば自動的に再パースする。
 */
public class ParsedFileCache {

    private static final ParsedFileCache INSTANCE = new ParsedFileCache();

    public static ParsedFileCache getInstance() { return INSTANCE; }

    private static class Entry {
        final CompilationUnit cu;
        final FileTime lastModified;

        Entry(CompilationUnit cu, FileTime lastModified) {
            this.cu = cu;
            this.lastModified = lastModified;
        }
    }

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    /**
     * キャッシュから CompilationUnit を取得する。
     * ファイルが更新されていれば再パースしてキャッシュを更新する。
     * パース失敗時は null を返す。
     */
    public CompilationUnit get(String filePath, JavaParser parser) {
        try {
            File file = new File(filePath);
            FileTime currentModified = Files.getLastModifiedTime(file.toPath());

            Entry entry = cache.get(filePath);
            if (entry != null && entry.lastModified.equals(currentModified)) {
                return entry.cu;
            }

            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
            if (cu != null) {
                cache.put(filePath, new Entry(cu, currentModified));
            }
            return cu;
        } catch (Exception e) {
            return null;
        }
    }

    /** 指定ファイルのキャッシュを明示的に無効化する。 */
    public void invalidate(String filePath) {
        cache.remove(filePath);
    }

    /** キャッシュを全クリアする。 */
    public void clear() {
        cache.clear();
    }
}
