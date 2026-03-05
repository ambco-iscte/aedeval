package evaluator.messages;

import evaluator.algorithmic.OrderOfGrowth;
import evaluator.algorithmic.OrderOfGrowthEstimator;
import evaluator.annotations.Test;
import extensions.Extensions;

import java.lang.reflect.Method;

public class MethodRuntimeComplexity extends Result {

    private final Method method;
    private final OrderOfGrowth expected;
    private final double confidence;
    private final OrderOfGrowthEstimator.Fit actual;

    public MethodRuntimeComplexity(Test test, Method method, OrderOfGrowth expected, double confidence, OrderOfGrowthEstimator.Fit actual) {
        super(test);
        this.method = method;
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
        return expected == actual.regression().model() && actual.confidence() >= confidence;
    }

    @Override
    public String getMessage() {
        String message = "%s was expected to be O(%s) with at least %s confidence".formatted(
            Extensions.signature(method),
            expected,
            (int) (.5 + 100 * confidence) + "%"
        );
        if (!passed())
            message += ", but was determined to be O(%s) with %s confidence.".formatted(
                actual.regression().model(),
                (int) (.5 + 100 * actual.confidence()) + "%"
            );
        else message += ".";
        return message;
    }
}
