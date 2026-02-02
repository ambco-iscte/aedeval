package aed;

import de.jplag.JPlagResult;
import de.jplag.reporting.reportobject.ReportObjectFactory;
import evaluator.Report;
import evaluator.Submission;
import evaluator.Tester;
import evaluator.FullEvaluator;
import extensions.Console;
import report.XLSXReportWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Executer {

	private static final int THREADS = 20;
	private static final int SUBMISSION = 10; // Submission Number.
	private static final int PLAGIARISM_CLUSTER_MINIMUM_SIZE = 5; // This many students (or more) to warn of plagiarism.
	private static final String ROOT = System.getProperty("user.dir") + File.separator + "submissions";
	private static final String PARENT = ROOT + File.separator + "submission" + SUBMISSION;

	static {
		try {
			Path rootDirectory = Path.of(ROOT);
			if (Files.notExists(rootDirectory))
				Files.createDirectory(rootDirectory);

			Path parentDirectory = Path.of(PARENT);
			if (Files.notExists(parentDirectory))
				Files.createDirectory(parentDirectory);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends Tester> getTester() throws ClassNotFoundException {
		Class<?> type = Class.forName("aed.testers.TestSubmission" + SUBMISSION);
		if (Tester.class.isAssignableFrom(type))
			return (Class<? extends Tester>) type;
		throw new ClassNotFoundException("Could not find tester class: aed.testers.TestSubmission" + SUBMISSION);
	}

	public static void main(String[] args) throws Exception {
		if (Files.exists(Path.of("reports", "Report - Submission " + SUBMISSION + ".xlsx")))
			Console.warning("A report file already exists for submission " + SUBMISSION + ". " +
					"Are you sure this is the submission you want to evaluate?" + System.lineSeparator());

		Report report = new FullEvaluator<>(
				PARENT, 								// Folder containing student submissions.
				"Submission " + SUBMISSION, 			// Description.
				getTester()								// Tester class.
		).run(THREADS);
		System.out.println();

		plagiarism(report);								// Write plagiarism report and print clusters to console.
		write(report);									// Write XLSX report.

		System.exit(0);
	}

	// https://jplag.github.io/JPlag/
	private static void plagiarism(Report report) throws IOException {
		if (report.hasPlagiarismAnalysis()) {
			JPlagResult result = report.getPlagiarismAnalysis();

			// Write report.
			Path reportPath = Path.of("reports", "jplag", "jplag-submission" + SUBMISSION + ".zip");
			ReportObjectFactory reportObjectFactory = new ReportObjectFactory(reportPath.toFile());
			reportObjectFactory.createAndSaveReport(result);

			// Print clusters to console.
			for (Set<de.jplag.Submission> cluster : report.getTotalPlagiarismClusters()) {
				Console.warning("Found cluster of " + cluster.size() + " students with 100% code similarity:");
				for (de.jplag.Submission entry : cluster) {
					String name = entry.getName().split("_")[0];
					System.out.println("\t• " + name);
				}
				System.out.println();
			}
		}
	}

	private static void write(Report report) throws IOException {
        List<Report.Entry> entries = report.getEntries().stream().map(entry -> {
			Submission submission = entry.getSubmission();
			long id = Long.parseLong(submission.getDirectory().getName().split("_")[1]);
			String name = submission.getDirectory().getName().split("_")[0];

			Submission sub = new Submission(submission.getDirectory(), name, id, submission.getExpectedFiles());
			return new Report.Entry(sub, entry.getResults(), entry.getGrade());
		}).sorted(Comparator.comparing(o -> o.getSubmission().getName())).toList();

		String name = "Report - Submission " + SUBMISSION;
		Report processed = new Report(report.getDescription(), entries, report.getPlagiarismAnalysis());

		Path reportsFolder = Path.of("reports");
		if (Files.notExists(reportsFolder))
			Files.createDirectory(reportsFolder);

		XLSXReportWriter.write(processed, "reports" + File.separator + name, PLAGIARISM_CLUSTER_MINIMUM_SIZE);
	}
}
