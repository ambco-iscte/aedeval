package evaluator;

import extensions.Files;
import extensions.out.Console;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
