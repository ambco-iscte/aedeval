package evaluator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import de.jplag.JPlag;
import de.jplag.JPlagResult;
import de.jplag.exceptions.ExitException;
import de.jplag.options.JPlagOptions;
import de.jplag.java.JavaLanguage;

import de.jplag.reporting.reportobject.ReportObjectFactory;
import extensions.*;
import loading.ClassLoader;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.LoggerFactory;

/**
 * Class used for testing a batch of source code files and collecting all results.
 *
 * @author Afonso Caniço
 */
public class FullEvaluator<T extends Tester> {

	private static final long SUBMISSION_TIMEOUT_MINUTES = 5L;

	private ExecutorService THREAD_POOL;

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
	 */
	public Report run(int threads) {
		try {
			// Set thread pool
			THREAD_POOL = Executors.newFixedThreadPool(threads);

			// Validate Submitted Files
			Map<File, Submission> submissions = validateSubmissions();
			System.out.println();

			Report report = new Report(description);

			// Run Plagiarism Checker (before evaluator cleans code files!)
			if (submissions.size() >= 2)
				report.setPlagiarismAnalysis(checkPlagiarism());

			// Evaluate Student Submissions
			evaluateAllFiles(submissions, report);
			ClassLoader.flush();

			// Restore Student Code Files from Backups
			restoreSubmissionCodeFiles(submissions);

			return report;
		} catch (ExecutionException | InterruptedException | IOException e) {
			Console.error("Exception thrown when running full evaluation: " + e.getMessage());
			throw new RuntimeException(e);
		}
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

	private void restoreSubmissionCodeFiles(Map<File, Submission> submissions) {
		for (File submissionDirectory : submissions.keySet()) {
			for (File file : Files.walk(submissionDirectory)) {
				String extension = FilenameUtils.getExtension(file.getName());
				if (extension.equals("java")) {
					File parent = file.getParentFile();
					File backup = Path.of(parent.getPath(), Files.getNameWithoutExtension(file) + "." + ClassLoader.BACKUP_FILE_EXTENSION).toFile();
					if (backup.exists() && file != backup) {
						if (file.delete()) {
							if (!backup.renameTo(file))
								Console.warning("Could not restore backup file: " + backup.getPath());
						} else Console.warning("Could not delete temporary code file: " + file.getPath());
					}
				} else file.deleteOnExit();
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

			System.out.println("Processed " + validSubmissionCount + " submissions (out of " + submissions.length + ") successfully!");
			System.out.println("Elapsed time: " + ((end - start) / 1000.0) + " seconds");
		}

		return submissionMap;
	}

	private void evaluateAllFiles(Map<File, Submission> submissions, Report report) throws ExecutionException, InterruptedException {
		System.out.println("Evaluating All Submissions...");
		long start = System.currentTimeMillis();

		// Progress bar! Fancy :)
		ProgressBar progress = new ProgressBar(2 * submissions.size(), 175, "[" + description + "] Evaluating...");

		// Analyse all submissions in parallel and wait for everything to be finished
		List<Future<Tester>> analysed = THREAD_POOL.invokeAll(
				getEvaluationTasks(submissions, tester, progress),
				submissions.size() * SUBMISSION_TIMEOUT_MINUTES,
				TimeUnit.MINUTES
		);

		for (Future<Tester> future : analysed) {
			Tester test = future.get();
			Submission submission = test.getSubmission();

			double grade = test.grade();
			report.add(submission, test.getResults(), grade);
			progress.step();
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
	private static List<Callable<Tester>> getEvaluationTasks(Map<File, Submission> submissions, Class<? extends Tester> tester, ProgressBar progress) {
		List<Callable<Tester>> tasks = new ArrayList<>();
		for (File subDir : submissions.keySet()) {
			if (subDir.isDirectory()) {
				tasks.add(new Runnable(submissions.get(subDir), tester, progress));
			}
		}
		return tasks;
	}
}