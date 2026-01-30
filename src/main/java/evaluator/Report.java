package evaluator;

import de.jplag.JPlagComparison;
import de.jplag.JPlagResult;
import evaluator.annotations.Test;
import evaluator.messages.Result;
import extensions.DisjointSet;

import java.io.*;
import java.util.*;

public class Report implements Iterable<Report.Entry>, Serializable {

    public static class Entry {

        private final Submission submission;

        private final Map<Test, List<Result>> results;

        private final double grade;

        public Entry(Submission submission, Map<Test, List<Result>> results, double grade) {
            this.submission = submission;
            this.results = results;
            this.grade = grade;
        }

        public Submission getSubmission() {
            return submission;
        }

        public Map<Test, List<Result>> getResults() {
            return results;
        }

        public double getGrade() {
            return grade;
        }

        public Map<String, Integer> getErrorCountPerCode() {
            Map<String, Integer> map = new HashMap<>();
            for (Test test : results.keySet()) {
                for (Result result : results.get(test)) {
                    if (!result.passed())
                        map.put(result.errorCode(), map.getOrDefault(result.errorCode(), 0) + 1);
                }
            }
            return map;
        }

        public Set<String> getErrorCodes() {
            Set<String> set = new TreeSet<>();
            for (Test test : results.keySet()) {
                for (Result result : results.get(test)) {
                    if (!result.passed())
                        set.add(result.errorCode());
                }
            }
            return set;
        }

        public List<String> getErrorMessages() {
            List<String> list = new ArrayList<>();
            for (Test test : results.keySet()) {
                for (Result result : results.get(test)) {
                    if (!result.passed()) {
                        if (result.getTest() == null)
                            list.add(result.getMessage());
                        else
                            list.add("[" + result.getTest().description() + "] " + result.getMessage());
                    }
                }
            }
            return list;
        }
    }

    private final String description;

    private final List<Entry> entries;

    private JPlagResult plagiarismAnalysis;

    private Iterable<Set<de.jplag.Submission>> equalCodeClusters;

    public Report() {
        this.entries = new ArrayList<>();
        this.description = "";
    }

    public Report(String description) {
        this.entries = new ArrayList<>();
        this.description = description;
    }

    public Report(String description, List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
        this.description = description;
    }

    public Report(String description, List<Entry> entries, JPlagResult plagiarismAnalysis) {
        this.entries = new ArrayList<>(entries);
        this.description = description;
        setPlagiarismAnalysis(plagiarismAnalysis);
    }

    void add(Submission submission, Map<Test, List<Result>> results, double grade) {
        entries.add(new Entry(submission, results, grade));
    }

    public String getDescription() {
        return description;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    void setPlagiarismAnalysis(JPlagResult result) {
        this.plagiarismAnalysis = result;
        this.equalCodeClusters = getEqualSubmissionClusters();
    }

    public JPlagResult getPlagiarismAnalysis() {
        return plagiarismAnalysis;
    }

    public boolean hasPlagiarismAnalysis() {
        return plagiarismAnalysis != null && equalCodeClusters != null;
    }

    public Iterable<Set<de.jplag.Submission>> getTotalPlagiarismClusters() {
        return equalCodeClusters;
    }

    private de.jplag.Submission getJPlagSubmission(Entry entry) {
        for (de.jplag.Submission submission : plagiarismAnalysis.getSubmissions().getSubmissions()) {
            if (submission.getName().startsWith(entry.submission.getName()))
                return submission;
        }
        return null;
    }

    public Set<de.jplag.Submission> getPlagiarismCluster(Entry entry) {
        de.jplag.Submission submission = getJPlagSubmission(entry);
        for (Set<de.jplag.Submission> cluster : equalCodeClusters) {
            if (cluster.contains(submission))
                return cluster;
        }
        return null;
    }

    // Average O(N²α(N)) ~ O(N²), worst O(N³)
    private Iterable<Set<de.jplag.Submission>> getEqualSubmissionClusters() {
        DisjointSet<de.jplag.Submission> set = new DisjointSet<>();
        for (JPlagComparison comparison : plagiarismAnalysis.getAllComparisons()) { // Average O(N²α(N)), worst O(N³)
            if (comparison.similarity() >= 1) {
                de.jplag.Submission first = comparison.firstSubmission();
                de.jplag.Submission second = comparison.secondSubmission();
                set.put(first);
                set.put(second);
                set.union(first, second);
            }
        }
        return set.components(); // Average O(Nα(N)), worst O(N²α(N))
    }

    public void print() {
        for (Entry entry : entries) {
            System.out.printf("[%f] %s\n", entry.grade, entry.submission.getName());
            for (Test test : entry.results.keySet()) {
                System.out.printf("\t➤ %s\n", test.description());
                for (Result result : entry.results.get(test))
                    System.out.printf("\t\t• %s\n", result.getMessage());
            }
            System.out.println();
        }
    }

    @Override
    public Iterator<Entry> iterator() {
        return entries.iterator();
    }
}
