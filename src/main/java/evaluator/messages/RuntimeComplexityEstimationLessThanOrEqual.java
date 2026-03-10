package evaluator.messages;

import evaluator.algorithmic.OrderOfGrowth;
import evaluator.algorithmic.OrderOfGrowthEstimator;
import evaluator.annotations.Test;

@Result.AlwaysShowInReport
public class RuntimeComplexityEstimationLessThanOrEqual extends Result {

    private final String description;
    private final OrderOfGrowth expected;
    private final double confidence;
    private final OrderOfGrowthEstimator.Fit actual;

    private enum Verdict {
        PASS_CONFIDENT, // High Confidence, Low Ambiguity
        FAIL_CONFIDENT, // Low Confidence, Low Ambiguity

        PASS_AMBIGUOUS, // High Confidence, High Ambiguity
        FAIL_AMBIGUOUS, // Low Confidence, High Ambiguity

        INCONCLUSIVE
    }

    public RuntimeComplexityEstimationLessThanOrEqual(Test test, String description, OrderOfGrowth expected, double confidence, OrderOfGrowthEstimator.Fit actual) {
        super(test);
        this.description = description;
        this.expected = expected;
        this.confidence = confidence;
        this.actual = actual;
    }

    @Override
    public String errorCode() {
        return "Incorrect Runtime Complexity";
    }

    @Override
    public boolean passed() {
        return verdict() != Verdict.FAIL_CONFIDENT && verdict() != Verdict.FAIL_AMBIGUOUS;
    }

    private boolean lte(OrderOfGrowth model) {
        return model.compareTo(expected) <= 0;
    }

    private Verdict verdict() {
        if (highConfidenceLowAmbiguity())
            return lte(actual.best().model()) ? Verdict.PASS_CONFIDENT : Verdict.FAIL_CONFIDENT;
        if (highConfidenceHighAmbiguity())
            return lte(actual.best().model()) || lte(actual.secondBest().model()) ? Verdict.PASS_AMBIGUOUS : Verdict.FAIL_AMBIGUOUS;
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

        return switch(verdict()) {
            case PASS_CONFIDENT -> """
            The runtime complexity of %s is less than or equal to O(%s) with %s confidence and %s ambiguity.
            """.formatted(description, best, actualConfidence, actualAmbiguity).trim();

            case FAIL_CONFIDENT -> """
            The runtime complexity of %s was expected to be less than or equal to O(%s) with at least %s confidence
            and at most %s ambiguity, but was determined to be O(%s) with %s confidence and %s ambiguity.
            """.formatted(description, expected, expectedMinConfidence, expectedMaxAmbiguity, best, actualConfidence, actualAmbiguity).trim();

            case PASS_AMBIGUOUS -> """
            The runtime complexity of %s was %s ambiguous between O(%s) and O(%s), and was determined to be
            less than or equal to O(%s) with %s confidence.
            """.formatted(description, actualAmbiguity, best, second, expected, actualConfidence).trim();

            case FAIL_AMBIGUOUS -> """
            The runtime complexity of %s was %s ambiguous between O(%s) and O(%s), was determined to be less than or
            equal to O(%s) with %s confidence, but should have been O(%s) with at least %s confidence.
            """.formatted(description, actualAmbiguity, best, second, best, actualConfidence, expected, expectedMinConfidence).trim();

            case INCONCLUSIVE -> """
            The runtime complexity of %s was expected to be less than or equal to O(%s) with at least %s confidence and
            at most %s ambiguity, but results were inconclusive (this is the evaluator's fault, not yours).
            """.formatted(description, expected, expectedMinConfidence, expectedMaxAmbiguity).trim();
        };
    }
}
