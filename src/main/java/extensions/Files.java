package extensions;

import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.util.*;

public class Files {

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
            boolean nameIsSimilar = child.getName().equals(name) || lev.similar(child.getName(), name, 0.2);
            if (child.isFile() && nameIsSimilar)
                return child;
        }
        return null;
    }

    public static String getNameWithoutExtension(File file) {
        return FilenameUtils.getBaseName(file.getName());
    }
}
