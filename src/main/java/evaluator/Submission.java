package evaluator;

import extensions.Console;
import extensions.Files;
import loading.ClassLoader;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts and lists all files submitted by each student.
 *
 * @author Caroline Conti
 * @author Afonso Caniço
 */
public class Submission implements Serializable {

	private final File dir;
	private final String name;
	private final long id;
	private final List<String> files;
	private final List<String> expected;
	private boolean isValid; // Does the submission contain the expected files?
	private Map<String, Boolean> contains; // Does the submission contain the file with the given name?

	public Submission(File dir, String name, long id, List<String> expected) {
		if (!dir.isDirectory()) {
			Console.error("Supplied submission directory is not a valid folder: " + dir.getPath());
			isValid = false;
		}
		this.dir = dir;
		this.name = name;
		this.id = id;
		this.expected = expected;
		this.files = new ArrayList<>();

		List<String> exp = new ArrayList<>(expected);
		for (File file : Files.walk(dir)) {
			String n = file.getName();

			// Restore backup if present
			/*
			if (FilenameUtils.getExtension(n).equalsIgnoreCase(ClassLoader.BACKUP_FILE_EXTENSION)) {
				Path dest = Path.of(file.getParent(), Files.getNameWithoutExtension(file) + ".java");
				try {
					java.nio.file.Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) { }
			}

			 */

			files.add(n);
			exp.remove(n);
		}
		isValid = exp.isEmpty();
	}

	public Submission(File dir, List<String> expected) {
		this(dir, dir.getName(), dir.hashCode(), expected);
	}

	public Submission(File dir, String... expected) {
		this(dir, Arrays.asList(expected));
	}

	public Submission(File dir, Class<? extends Tester> tester) {
		this(dir, Tester.getAllRequiredFiles(tester).stream().toList());
	}

	public String getName() {
		return name;
	}

	public long getID() {
		return id;
	}

	public List<String> getFiles() {
		return files;
	}

	public List<String> getExpectedFiles() {
		return expected;
	}

	public File getDirectory() {
		return dir;
	}

	public String getPath() {
		return dir.getPath();
	}

	public boolean isValid() {
		return isValid;
	}
}
