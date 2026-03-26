package evaluator;

import de.jplag.JPlag;
import de.jplag.JPlagResult;
import de.jplag.exceptions.ExitException;
import de.jplag.java.JavaLanguage;
import de.jplag.options.JPlagOptions;
import extensions.Extensions;
import extensions.Files;
import extensions.out.Console;
import extensions.out.ProgressBar;
import loading.ClassLoader;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;

/**
 * The {@code FullEvaluator} class is used to evaluate many student submissions to the same assignment.
 *
 * <p>Evaluation requires at least three arguments:</p>
 * <ol>
 *     <li>The path to the folder containing the submissions;</li>
 *     <li>A description, usually corresponding to an assignment's name or title.</li>
 *     <li>A class inheriting {@link Tester} where the unit tests are defined.</li>
 * </ol>
 *
 * <p>In this way, evaluation can be performed by instantiating a new evaluator and calling the {@link #run()} method:</p>
 *
 * <pre>{@code
 * String folder = "path/to/folder/containing/all/submissions";
 * Report results = new FullEvaluator<>(folder, "Title", MyTester.class).run();
 * }</pre>
 *
 * <p>If there are many submissions, you may want to enable multithreading. This is done through the {@link #run(int)}
 * method, which takes the number of threads to use. Both methods produce an evaluation {@link Report}.</p>
 *
 * <p>Sometimes, students may submit source code files with names that are different yet similar to those intended.
 * If you want the evaluator to automatically search for files with similar names, you can pass a
 * {@code fileNameSimilarityThreshold} parameter which defines how similar a file name must be to the intended name to
 * be considered (0 to 1, default = 0.8).</p>
 *
 * <pre>{@code
 * new FullEvaluator<>("path/to/folder", "Title", MyTester.class, 0.95);
 * }</pre>
 *
 * <p>Matching of similar file names is based on the {@link extensions.Levenshtein} distance.</p>
 *
 * @see Tester
 * @see Submission
 * @author Afonso Caniço
 */
public class FullEvaluator<T extends Tester> {

    private ExecutorCompletionService<Tester> EXECUTOR;

    private static final String BACKUP_FILE_EXTENSION = "backup";
    private boolean backupActive = false;

	private final Class<T> tester;
	private final List<String> expected;
    private final double fileNameSimilarityThreshold;
	private final String root;
	private final String description;
	private File referenceCodeFolder;

    /**
     * The {@code FullEvaluator} class is used to evaluate many student submissions to the same assignment.
     * @param root Path to the folder containing student submissions.
     * @param description Assignment or task description or title.
     * @param tester {@link Tester} class to use.
     * @param fileNameSimilarityThreshold Threshold (0..1) for file name similarity when a file cannot be found through its exact name (see {@link loading.SourceLookup}).
     */
	public FullEvaluator(String root, String description, Class<T> tester, double fileNameSimilarityThreshold) {
        if (fileNameSimilarityThreshold < 0 || fileNameSimilarityThreshold > 1)
            throw new IllegalArgumentException("File name similarity threshold must be >= 0 and <= 1.");

		this.root = root;
		this.description = description;
		this.tester = tester;
		this.expected = Tester.getAllRequiredFiles(tester).stream().toList();
        this.fileNameSimilarityThreshold = fileNameSimilarityThreshold;
	}

    public FullEvaluator(String root, String description, Class<T> tester) {
        this(root, description, tester, 0.8);
    }

	public FullEvaluator<T> withReference(File folder) {
		this.referenceCodeFolder = folder;
		return this;
	}

    private void terminate(Map<File, Submission> submissions, boolean early) {
        if (early)
            Console.warning("Early shutdown! Restoring submission files.");
        restoreSubmissionCodeFiles(submissions);
        try { ClassLoader.clear(); } catch (IOException ignored) { }
    }

	/**
	 * Validates all files and evaluates all source code files present in the parent directory.
     * Execution is asynchronous with a fixed number of threads.
     * @param threads Number of threads to use.
	 */
	public @NotNull Report run(int threads) {
        // Set thread pool
        EXECUTOR = new ExecutorCompletionService<>(Executors.newFixedThreadPool(threads));

        // Validate Submitted Files
        Map<File, Submission> submissions = validateSubmissions();
        System.out.println();

        // Backup Student Submission Files
        backupSubmissionFiles(submissions);

        // Create Report
        Report report = new Report(description);

        // Restore submission backup files if the program is terminated early!
        Thread restoreOnShutdown = new Thread(() -> terminate(submissions, true));
        Runtime.getRuntime().addShutdownHook(restoreOnShutdown);

		try {
            long valid = submissions.values().stream().filter(Submission::isValid).count();
			if (valid >= 2)
				report.setPlagiarismAnalysis(checkPlagiarism());
            else
                Console.warning("Cannot run JPlag plagiarism analysis with only " + valid + " valid submission(s).");
			evaluateAllFiles(submissions, report);
		} catch (Exception e) {
			Console.error(e.getClass().getCanonicalName() + " thrown when running full evaluation: " + e.getMessage());
		}

        terminate(submissions, false);
        Runtime.getRuntime().removeShutdownHook(restoreOnShutdown);

        return report;
	}

    /**
     * Validates all files and evaluates all source code files present in the parent directory.
     * This is equivalent to calling {@link #run(int)} with {@code threads = 1}.
     */
    public Report run() {
        return run(1);
    }

	private JPlagResult checkPlagiarism() {
		JavaLanguage language = new JavaLanguage();
		Set<File> submissionDirectories = Set.of(new File(root));

		JPlagOptions options = new JPlagOptions(language, submissionDirectories, Set.of())
				.withFileSuffixes(List.of("java")) // Include only Java files
				.withNormalize(true) // Normalise token order
				.withExclusionFileName(".jplag/exclude.txt");

        if (!options.excludedFiles().isEmpty())
		    Console.warning("JPlag is ignoring the following files: " + Extensions.joinToString(options.excludedFiles()));

		if (referenceCodeFolder != null)
			options = options.withBaseCodeSubmissionDirectory(referenceCodeFolder); // Reference code

		try {
			System.out.println("Running plagiarism analysis using JPlag...");
			long start = System.currentTimeMillis();
			JPlagResult result = JPlag.run(options);
			long end = System.currentTimeMillis();
			System.out.println("Done! Elapsed time: " + ((end - start) / 1000.0) + " seconds." + System.lineSeparator());
			return result;
		} catch (ExitException e) {
			Console.error("Exception thrown when running plagiarism analysis: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}

    private void backupSubmissionFiles(Map<File, Submission> submissions) {
        if (backupActive)
            return;
        for (File submissionDirectory : submissions.keySet()) {
            for (File file : Files.walk(submissionDirectory)) {
                if (file.isFile()) {
                    if (FilenameUtils.isExtension(file.getName(), BACKUP_FILE_EXTENSION))
                        file.delete();
                    else {
                        File dir = file.getParentFile();
                        Path backup = Path.of(dir.toString(), file.getName() + "." + BACKUP_FILE_EXTENSION);
                        try {
                            java.nio.file.Files.copy(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            Console.warning("Could not backup file " + file.getPath() + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
        backupActive = true;
    }

	private void restoreSubmissionCodeFiles(Map<File, Submission> submissions) {
        if (!backupActive)
            return;

        for (File submissionDirectory : submissions.keySet()) {
            for (File file : Files.walk(submissionDirectory)) {
                if (file.isFile()) {
                    String extension = FilenameUtils.getExtension(file.getName());
                    if (!extension.equals(BACKUP_FILE_EXTENSION) && !file.delete())
                        file.deleteOnExit();
                }
            }
        }

		for (File submissionDirectory : submissions.keySet()) {
			for (File file : Files.walk(submissionDirectory)) {
				String extension = FilenameUtils.getExtension(file.getName());
				if (file.exists() && file.isFile() && extension.equals(BACKUP_FILE_EXTENSION)) {
					File parent = file.getParentFile();
					Path restore = Path.of(parent.getPath(), Files.getNameWithoutExtension(file));
                    try {
                        java.nio.file.Files.copy(file.toPath(), restore, StandardCopyOption.REPLACE_EXISTING);
                        if (!file.delete()) {
                            file.deleteOnExit();
                            // Console.warning("Could not delete backup file: " + file.getPath());
                        }
                    } catch (IOException e) {
                        Throwable cause = e.getCause();
                        String message = e.getMessage();
                        if (cause != null)
                            message = cause.getMessage();
                        Console.warning("Could not restore backup file " + file.getPath() + ": " + message);
                    }

				}
			}
		}

        backupActive = false;
	}

	private Map<File, Submission> validateSubmissions() {
		Map<File, Submission> submissionMap = new LinkedHashMap<>();

		System.out.println("Checking Submission Files...");

		// Number of valid submissions
		int validSubmissionCount = 0;

		long start = System.currentTimeMillis();

		// Go through parent directory and validate files of all subdirectories
		File directory = new File(root);
		if (directory.isDirectory()) {
			File[] submissions = directory.listFiles(File::isDirectory);
			assert submissions != null;

			for (File subDir : submissions) {
				if (subDir.isDirectory()) {
					Submission submission = new Submission(subDir, expected);
					submissionMap.putIfAbsent(subDir, submission);
					if (submission.isValid())
						validSubmissionCount++;
				} else
					Console.warning("Check for submitted files in " + subDir.getName() + "!");
			}
			long end = System.currentTimeMillis();

			System.out.println("Valid files for " + validSubmissionCount + " submissions (out of " + submissions.length + ").");
			System.out.println("Elapsed time: " + ((end - start) / 1000.0) + " seconds");
		}

		return submissionMap;
	}

	private void evaluateAllFiles(Map<File, Submission> submissions, Report report) throws ExecutionException, InterruptedException {
		System.out.println("Evaluating All Submissions...");
		long start = System.currentTimeMillis();

		ProgressBar progress = new ProgressBar(submissions.size(), 150, "[" + description + "] Evaluating...");

        List<Callable<Tester>> tasks = getEvaluationTasks(submissions, tester);
        for (Callable<Tester> task : tasks) {
            EXECUTOR.submit(task);
        }

		for (int i = 0; i < tasks.size(); i++) {
            Future<Tester> future = EXECUTOR.take();
            Tester test = future.get();
            Submission submission = test.getSubmission();

            double grade = test.grade();
            report.add(submission, test.getResults(), grade);
            progress.step();

            System.gc();
		}

		long end = System.currentTimeMillis();
		System.out.println("Done! Elapsed time: " + ((end - start) / 1000.0) + " seconds");
	}

	/**
	 * Builds a list of Callable tasks corresponding to the task of evaluating each student submission.
	 * @param submissions An array containing the directory of each student's submission.
	 * @param tester The {@link Tester} class to use for submission testing and validation.
	 * @return A list of all callable tasks. See also: {@link ExecutorService#invokeAll(Collection)}.
	 */
	private List<Callable<Tester>> getEvaluationTasks(Map<File, Submission> submissions, Class<? extends Tester> tester) {
		List<Callable<Tester>> tasks = new ArrayList<>();
		for (File subDir : submissions.keySet()) {
			if (subDir.isDirectory()) {
				tasks.add(new TesterDispatcher(submissions.get(subDir), tester, fileNameSimilarityThreshold));
			}
		}
		return tasks;
	}
}