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
 * Class used for testing a batch of source code files and collecting all results.
 *
 * @author Afonso Caniço
 */
public class FullEvaluator<T extends Tester> {

	private static final long SUBMISSION_TIMEOUT_MINUTES = 5L;

    private static final String BACKUP_FILE_EXTENSION = "backup";

	private ExecutorCompletionService<Tester> EXECUTOR;

	private final Class<T> tester;

	private final List<String> expected;

	private final String root;

	private final String description;

	private File referenceCodeFolder;

	public FullEvaluator(String root, String description, Class<T> tester) {
		this.root = root;
		this.description = description;
		this.tester = tester;
		this.expected = Tester.getAllRequiredFiles(tester).stream().toList();
	}

	public FullEvaluator<T> withReference(File folder) {
		this.referenceCodeFolder = folder;
		return this;
	}

	/**
	 * Validates all files and evaluates all source code files present in the parent directory.
     * Execution is asynchronous with a fixed number of threads.
     * @param threads Number of parallel threads to use.
	 */
	public @NotNull Report run(int threads) {
        // Set thread pool
        EXECUTOR = new ExecutorCompletionService<>(Executors.newFixedThreadPool(threads));

        // Validate Submitted Files
        Map<File, Submission> submissions = validateSubmissions();
        System.out.println();

        // Backup Student Submission Files
        backupSubmissionFiles(submissions);

        Report report = new Report(description);

		try {
			// Run Plagiarism Checker (before evaluator cleans code files!)
			if (submissions.size() >= 2)
				report.setPlagiarismAnalysis(checkPlagiarism());

			// Evaluate Student Submissions
			evaluateAllFiles(submissions, report);
			ClassLoader.flush();
		} catch (Exception e) {
			Console.error(e.getClass().getCanonicalName() + " thrown when running full evaluation: " + e.getMessage());
		}

        restoreSubmissionCodeFiles(submissions);
        try { ClassLoader.flush(); } catch (IOException ignored) { }

        return report;
	}

    /**
     * Validates all files and evaluates all source code files present in the parent directory.
     * Execution is synchronous. This is equivalent to calling {@link #run(int)} with {@code threads = 1}.
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

		Console.warning("JPlag is ignoring the following files: " + Extensions.joinToString(options.excludedFiles()));

		if (referenceCodeFolder != null)
			options = options.withBaseCodeSubmissionDirectory(referenceCodeFolder); // Reference code

		try {
			System.out.println("Running plagiarism analysis using JPlag...");
			long start = System.currentTimeMillis();
			JPlagResult result = JPlag.run(options);
			long end = System.currentTimeMillis();
			System.out.println("Done! Elapsed time: " + ((end - start) / 1000.0) + " seconds.\n");
			return result;
		} catch (ExitException e) {
			Console.error("Exception thrown when running plagiarism analysis: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}

    private void backupSubmissionFiles(Map<File, Submission> submissions) {
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
    }

	private void restoreSubmissionCodeFiles(Map<File, Submission> submissions) {
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
            Future<Tester> future = EXECUTOR.take(); // Process testers as they finish (waits for a task to finish)
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
	private static List<Callable<Tester>> getEvaluationTasks(Map<File, Submission> submissions, Class<? extends Tester> tester) {
		List<Callable<Tester>> tasks = new ArrayList<>();
		for (File subDir : submissions.keySet()) {
			if (subDir.isDirectory()) {
				tasks.add(new TesterDispatcher(submissions.get(subDir), tester));
			}
		}
		return tasks;
	}
}