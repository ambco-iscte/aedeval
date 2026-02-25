package extensions;

import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class Files {

    public static File removeCopyIndicesFromFile(File file, boolean rename) {
        // Cleanup file name
        String name = Files.getNameWithoutExtension(file).trim();
        name = name.replaceAll("\\([0-9]+\\)", "").trim(); // Remove e.g. (1) for file copies.
        name = name + "." + FilenameUtils.getExtension(file.getName());

        Path path = Path.of(file.getParentFile().getPath(), name);
        if (rename)
            file.renameTo(path.toFile());
        return path.toFile();
    }

    public static Iterable<File> walk(File root) {
        return () -> new Iterator<>() {
            private final Queue<File> queue = new ArrayDeque<>(List.of(Objects.requireNonNull(root.listFiles())));

            @Override
            public boolean hasNext() {
                return !queue.isEmpty();
            }

            @Override
            public File next() {
                File next = queue.poll();
                if (next != null && next.isDirectory()) {
                    for (File child : Objects.requireNonNull(next.listFiles()))
                        queue.offer(child);
                }
                return next;
            }
        };
    }

    public static File findDescendant(File root, String name)  {
        for (File child : walk(root)) {
            if (child.isFile() && child.getName().equals(name))
                return child;
        }
        return null;
    }

    public static File findClosestDescendant(File root, String name) {
        Levenshtein lev = new Levenshtein();
        for (File child : walk(root)) {
            if (!FilenameUtils.getExtension(child.getName()).equals(FilenameUtils.getExtension(name)))
                continue;
            boolean nameIsSimilar = child.getName().equals(name) || lev.similarity(child.getName(), name) >= 0.8;
            if (child.isFile() && nameIsSimilar)
                return child;
        }
        return null;
    }

    public static File findClosestDescendantCopyInsensitive(File root, String name) {
        Levenshtein lev = new Levenshtein();
        for (File child : walk(root)) {
            if (!FilenameUtils.getExtension(child.getName()).equals(FilenameUtils.getExtension(name)))
                continue;
            File renamed = removeCopyIndicesFromFile(child, true);
            boolean nameIsSimilar = renamed.getName().equals(name) || lev.similarity(renamed.getName(), name) >= 0.8;
            if (renamed.isFile() && nameIsSimilar)
                return renamed;
        }
        return null;
    }

    public static String getNameWithoutExtension(File file) {
        return FilenameUtils.getBaseName(file.getName());
    }
}
