package report;

import evaluator.Report;
import evaluator.Submission;
import extensions.Extensions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class StaticXLSXReportWriter {

    private static final XLSXReportWriter INSTANCE;

    static {
        INSTANCE = new XLSXReportWriter() {
            @Override
            protected boolean isPlagiarised(Report report, Report.Entry entry) {
                return false;
            }

            @Override
            protected Optional<String> getMessage(Report report, Report.Entry entry) {
                return Optional.of(Extensions.joinToString(System.lineSeparator(), entry.getErrorMessages()));
            }

            @Override
            protected Long getStudentIDFromSubmission(Submission submission) {
                return submission.getID();
            }
        };
    }

    public static void write(Report report, Path path) throws IOException {
        INSTANCE.write(report, path);
    }
}
