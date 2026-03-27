package loading;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import extensions.Levenshtein;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

import static extensions.Extensions.tryOrElse;

public class SourceLookup {

    public interface Result extends Supplier<Optional<File>> {
        default Optional<CompilationUnit> getCompilationUnit() { return Optional.empty(); }
    }

    public record NotFound(String target) implements Result {
        @Override
        public Optional<File> get() {
            return Optional.empty();
        }
    }

    public record FoundWithExactName(String target, File result) implements Result {
        @Override
        public Optional<File> get() {
            return Optional.of(result);
        }
    }

    public record FoundWithSimilarName(String target, String actual, File result) implements Result {
        @Override
        public Optional<File> get() {
            return Optional.of(result);
        }
    }

    public record FoundWithFileAndClassNameMismatch(CompilationUnit unit, String target, String actual, String clazz, File result) implements Result {
        @Override
        public Optional<File> get() {
            return Optional.of(result);
        }

        @Override
        public Optional<CompilationUnit> getCompilationUnit() {
            return Optional.of(unit);
        }

        public boolean isCorrectFileName() {
            return target.equals(actual);
        }
    }

    public static Result lookup(File root, String name) {
        return lookup(root, name, 0.8, false);
    }

    public static Result lookup(File root, String name, double threshold) {
        return lookup(root, name, threshold, false);
    }

    public static Result lookup(File root, String name, double threshold, boolean rename) {
        if (!FilenameUtils.getExtension(name).equals("java"))
            throw new IllegalArgumentException("Target file must be a .java file!");

        for (File child : extensions.Files.walk(root)) {
            if (!FilenameUtils.getExtension(child.getName()).equals("java"))
                continue;
            String originalFileName = child.getName();

            File renamed = extensions.Files.removeCopyIndicesFromFile(child, true);
            if (!renamed.isFile())
                continue;

            CompilationUnit unit = tryOrElse(() -> StaticJavaParser.parse(child), null);

            // Is the file name equal to the target file name?
            if (name.equals(renamed.getName())) {
                if (unit != null) {
                    String primaryTypeName = Source.findFirstPublicTypeName(unit);
                    if (primaryTypeName != null && !FilenameUtils.getBaseName(name).equals(primaryTypeName))
                        return new FoundWithFileAndClassNameMismatch(unit, name, originalFileName, primaryTypeName, renamed);
                }
                return new FoundWithExactName(name, renamed);
            }

            File fixed = Path.of(renamed.getParentFile().getPath(), name).toFile();

            // Is the file name "close enough" to the target file name?
            // If not, does the file declare a primary type matching the target file name?
            if (new Levenshtein().similarity(renamed.getName(), name) >= threshold) {
                if (!rename || renamed.renameTo(fixed))
                    return new FoundWithSimilarName(name, originalFileName, renamed);
            } else {
                if (unit == null)
                    continue;
                String primaryTypeName = Source.findFirstPublicTypeName(unit);

                if (primaryTypeName != null && FilenameUtils.getBaseName(name).equals(primaryTypeName)) {
                    if (!rename || renamed.renameTo(fixed))
                        return new FoundWithFileAndClassNameMismatch(unit, name, originalFileName, primaryTypeName, renamed);
                }
            }
        }

        return new NotFound(name);
    }

}
