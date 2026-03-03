package loading;

import extensions.Console;
import extensions.Extensions;
import extensions.Files;
import loading.exceptions.ClassLoadingException;
import loading.exceptions.CompilationException;
import org.apache.commons.io.FilenameUtils;

import javax.tools.*;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ClassLoader {

    private static final java.util.List<URLClassLoader> loaders = new ArrayList<>();

    public static void flush() throws IOException {
        for (URLClassLoader loader : loaders) {
            loader.close();
        }
        loaders.clear();
    }

    // CAFEBABE :)
    private static boolean isCompiledJavaFile(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            int count = in.read(magic);
            if (count < 4)
                return false;
            return magic[0] == (byte) 0xCA &&
                   magic[1] == (byte) 0xFE &&
                   magic[2] == (byte) 0xBA &&
                   magic[3] == (byte) 0xBE;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Given a class name, compiles the .java file of that class.
     * @param className The class name.
     * @return The compiled .class file.
     */
    private static File compile(File directory, String className, List<String> options, String[] allowedPackages) throws FileNotFoundException, CompilationException {
        File javaFile = Files.findDescendant(directory, className);
        if (javaFile == null) {
            Console.error("File not found: " + directory.getPath() + "/" + className);
            throw new FileNotFoundException(className);
        }

        String extension = FilenameUtils.getExtension(javaFile.getName());
        if (isCompiledJavaFile(javaFile)) {
            if (extension.equals("class"))
                throw new CompilationException("Cannot compile a .class file. You must submit your .java file!");
            if (!extension.equals("java"))
                throw new CompilationException("Cannot compile .class file " + javaFile.getName() + ". Did you submit a ." + extension + " file renamed to .java?");
        }
        if (!extension.equals("java"))
            throw new CompilationException("Cannot compile file with unknown extension: " + extension + ". You must submit your .java file!");

        // Cleanup file name
        javaFile = Files.removeCopyIndicesFromFile(javaFile, true);

        // Clean source code using JavaParser :)
        try {
            Source.clean(javaFile, allowedPackages);
        } catch (Exception ignored) { }

        // Creates a Java compiler
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();

        // Create error message collector
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        // Create diagnostics collector
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        ArrayList<String> compilationOptions = new ArrayList<>(List.of("-encoding", "UTF-8", "-proc:none"));
        compilationOptions.addAll(options);

        // Compile .java file
        compile(javaFile, javac, diagnostics, errorStream, compilationOptions);

        return javaFile;
    }

    /**
     * Compiles a .java file and logs any compilation errors.
     * @param javaFile The .java {@link File} to compile.
     * @param compiler The JavaCompiler instance to use for compilation.
     * @param collector The DiagnosticCollector instance that collects compilation error.
     * @param errorStream The byte[] output stream where error messages are written.
     * @param options The file encoding options.
     */
    private static void compile(
            File javaFile,
            JavaCompiler compiler,
            DiagnosticCollector<JavaFileObject> collector,
            ByteArrayOutputStream errorStream,
            List<String> options
    ) throws CompilationException {
        // Compile .java file
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(collector, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(List.of(javaFile.getAbsolutePath()));
        JavaCompiler.CompilationTask task = compiler.getTask(new PrintWriter(errorStream), fileManager, collector, options, null, compilationUnits);
        boolean success = task.call();
        try {
            fileManager.close();
        } catch (IOException e1) {
            Console.error("Exception thrown when compiling file " + javaFile.getName() + ": " + e1.getMessage());
            //e1.printStackTrace();
        }

        // Log compilation errors
        if (!success)
            throw new CompilationException(collector.getDiagnostics().stream().filter(
                    x -> x.getKind() == Diagnostic.Kind.ERROR
            ).toList());
    }

    /**
     * Loads a Java class from a .java file.
     * @param javaFile The .java file.
     * @return The loaded class.
     * @throws IOException If an I/O error occurs when reading source files.
     * @throws CompilationException If the source file fails to compile.
     * @throws ClassLoadingException If the compiled class failed to load.
     */
    public static Class<?> load(File javaFile, String[] allowedPackages) throws IOException, ClassLoadingException, CompilationException {
        File dir = javaFile.getParentFile();
        File compiled = compile(dir, javaFile.getName(), List.of("-classpath", dir.getPath()), allowedPackages);
        try {
            // Create a ClassLoader instance for the directory the .java file is stored in
            URL[] classURLs = new URL[] { dir.toURI().toURL() };
            URLClassLoader classLoader = URLClassLoader.newInstance(classURLs); // DO NOT USE TRY WITH RESOURCES
            classLoader.setDefaultAssertionStatus(true);
            loaders.add(classLoader);
            return classLoader.loadClass(Files.getNameWithoutExtension(compiled));
        } catch (IllegalArgumentException | ClassNotFoundException | IOException | NoClassDefFoundError e) {
            throw new ClassLoadingException(javaFile, e);
        }
    }
}
