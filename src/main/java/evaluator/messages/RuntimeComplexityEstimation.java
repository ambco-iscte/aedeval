package evaluator.messages;

import evaluator.algorithmic.OrderOfGrowth;
import evaluator.algorithmic.OrderOfGrowthEstimator;
import evaluator.annotations.Test;

@Result.AlwaysShowInReport
public class RuntimeComplexityEstimation extends Result {

    public enum Comparison {
        EQUALS,
        LESS_THAN,
        LESS_THAN_OR_EQUAL
    }

    private final String description;
    private final OrderOfGrowth expected;
    private final double confidence;
    private final OrderOfGrowthEstimator.Fit actual;
    private final Comparison comparison;
    private final boolean amortised;

    private enum Verdict {
        PASS_CONFIDENT, // High Confidence, Low Ambiguity
        FAIL_CONFIDENT, // Low Confidence, Low Ambiguity

        PASS_AMBIGUOUS, // High Confidence, High Ambiguity
        FAIL_AMBIGUOUS, // Low Confidence, High Ambiguity

        INCONCLUSIVE
    }

    public RuntimeComplexityEstimation(
        Test test,
        String description,
        OrderOfGrowth expected,
        double confidence,
        OrderOfGrowthEstimator.Fit actual,
        Comparison comparison,
        boolean amortised
    ) {
        super(test);
        this.description = description;
        this.expected = expected;
        this.confidence = confidence;
        this.actual = actual;
        this.comparison = comparison;
        this.amortised = amortised;
    }

    @Override
    public String errorCode() {
        return "Incorrect Runtime Complexity";
    }

    @Override
    public boolean passed() {
        return verdict() != Verdict.FAIL_CONFIDENT && verdict() != Verdict.FAIL_AMBIGUOUS;
    }

    private boolean compare(OrderOfGrowth actual) {
        return switch (comparison) {
            case EQUALS -> actual == expected;
            case LESS_THAN -> actual.compareTo(expected) < 0;
            case LESS_THAN_OR_EQUAL -> actual.compareTo(expected) <= 0;
        };
    }

    private Verdict verdict() {
        if (highConfidenceLowAmbiguity())
            return compare(actual.best().model()) ? Verdict.PASS_CONFIDENT : Verdict.FAIL_CONFIDENT;
        if (highConfidenceHighAmbiguity())
            return compare(actual.best().model()) || compare(actual.secondBest().model()) ? Verdict.PASS_AMBIGUOUS : Verdict.FAIL_AMBIGUOUS;
        return Verdict.INCONCLUSIVE;
    }

    private boolean highConfidenceLowAmbiguity() {
        return actual.confidence() >= confidence && actual.ambiguity() <= 1 - confidence;
    }

    private boolean highConfidenceHighAmbiguity() {
        return actual.confidence() >= confidence && actual.ambiguity() > 1 - confidence;
    }

    @Override
    public String getMessage() {
        String actualConfidence = (int) (.5 + 100 * actual.confidence()) + "%";
        String actualAmbiguity = (int) (.5 + 100 * actual.ambiguity()) + "%";

        String expectedMinConfidence = (int) (.5 + 100 * confidence) + "%";
        String expectedMaxAmbiguity = (int) (.5 + 100 * (1 - confidence)) + "%";

        OrderOfGrowth best = actual.best().model();
        OrderOfGrowth second = actual.secondBest().model();

        String comp = switch (comparison) {
            case EQUALS -> "";
            case LESS_THAN -> "less than ";
            case LESS_THAN_OR_EQUAL -> "less than or equal to ";
        };

        String amortise = amortised ? "amortised " : "";

        return switch(verdict()) {
            case PASS_CONFIDENT -> """
            The %sruntime complexity of %s is %sO(%s) with %s confidence and %s ambiguity.
            """.formatted(amortise, description, comp, best, actualConfidence, actualAmbiguity).trim();

            case FAIL_CONFIDENT -> """
            The %sruntime complexity of %s was expected to be %sO(%s) with at least %s confidence
            and at most %s ambiguity, but was determined to be O(%s) with %s confidence and %s ambiguity.
            """.formatted(amortise, description, comp, expected, expectedMinConfidence, expectedMaxAmbiguity, best, actualConfidence, actualAmbiguity).trim();

            case PASS_AMBIGUOUS -> """
            The %sruntime complexity of %s was %s ambiguous between O(%s) and O(%s), and was determined to be
            %sO(%s) with %s confidence.
            """.formatted(amortise, description, actualAmbiguity, best, second, comp, expected, actualConfidence).trim();

            case FAIL_AMBIGUOUS -> """
            The %sruntime complexity of %s was %s ambiguous between O(%s) and O(%s), was determined to be %sO(%s) with
            %s confidence, but should have been O(%s) with at least %s confidence.
            """.formatted(amortise, description, actualAmbiguity, best, second, best, comp, actualConfidence, expected, expectedMinConfidence).trim();

            case INCONCLUSIVE -> """
            The %sruntime complexity of %s was expected to be %sO(%s) with at least %s confidence and
            at most %s ambiguity, but results were inconclusive (this is the evaluator's fault, not yours).
            """.formatted(amortise, description, comp, expected, expectedMinConfidence, expectedMaxAmbiguity).trim();
        };
    }
}
