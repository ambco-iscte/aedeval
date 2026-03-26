package evaluator.algorithmic;

import org.apache.commons.math3.distribution.FDistribution;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.jspecify.annotations.NonNull;

import java.util.*;

public abstract class OrderOfGrowthEstimator<T, E extends Throwable> {

    public static class Regression {

        private final OrderOfGrowth model;
        private final List<Sample> samples;
        private final OLSMultipleLinearRegression regression;
        private final double maxPredicted;
        private final double maxTime;

        public Regression(OrderOfGrowth model, List<Sample> samples, OLSMultipleLinearRegression regression) {
            this.model = model;
            this.samples = samples;
            this.regression = regression;

            maxPredicted = samples.stream().mapToDouble(model::predict).max().orElse(1.0);
            maxTime = samples.stream().mapToDouble(Sample::time).max().orElse(1.0);
        }

        public OrderOfGrowth model() {
            return model;
        }

        public List<Sample> samples() {
            return samples;
        }

        public OLSMultipleLinearRegression regression() {
            return regression;
        }

        public record FTestResult(double statistic, double pValue) { }

        /**
         * Returns the AIC (Akaike Information Criterion) for the regression.
         * @see <a href="https://en.wikipedia.org/wiki/Akaike_information_criterion">Akaike Information Criterion</a>
         */
        public double AIC() {
            int n = samples.size();
            return n * Math.log(RSS() / n) + 4.0;
        }

        /**
         * Calculates the regression's residual sum of squares (RSS).
         */
        public double RSS() {
            return regression.calculateResidualSumOfSquares();
        }

        public double RSquared() {
            return regression.calculateRSquared();
        }

        public double scalar() {
            return maxPredicted * regression.estimateRegressionParameters()[1];
        }

        public double intercept() {
            return (maxTime / maxPredicted) * regression.estimateRegressionParameters()[0];
        }

        public FTestResult fTest() {
            int df1 = 1;
            int df2 = samples.size() - 2;

            double tss = regression.calculateTotalSumOfSquares();
            double rss = regression.calculateResidualSumOfSquares();

            double statistic = ((tss - rss) / df1) / (rss / df2);
            double pValue = 1 - new FDistribution(df1, df2).cumulativeProbability(statistic);

            return new FTestResult(statistic, pValue);
        }

        @Override
        public @NonNull String toString() {
            return String.format("%f + %f * (%s)", intercept(), scalar(), model);
        }

        public String toStringWithoutIntercept() {
            return String.format("%f * (%s)", scalar(), model);
        }
    }

    public record Fit(Regression best, Regression secondBest, double confidence, double ambiguity) { }
    public record Sample(long n, double time) { }

    protected abstract T input(long n) throws E;
    protected abstract void action(T input) throws E;
    protected abstract long update(long n) throws E;

    public Fit fit(long initial, int steps, int repeats) throws E {
        return fit(calculateSamples(initial, steps, repeats), false);
    }

    public Fit fitAmortized(long initial, int steps, int repeats) throws E {
        return fit(calculateSamples(initial, steps, repeats), true);
    }

    private List<Sample> calculateSamples(long initial, int steps, int repeats) throws E {
        List<Sample> samples = new ArrayList<>();

        // JVM Warmup
        for (long n = initial, i = 0; i < steps; n = update(n), i++)
            getElapsedTimeNanos(n);

        for (long n = initial, i = 0; i < steps; n = update(n), i++) {
            long sum = 0L;
            for (int j = 0; j < repeats; j++)
                sum += getElapsedTimeNanos(n);
            samples.add(new Sample(n, (double) sum / repeats));
        }

        return samples;
    }

    public double calculateEstimatedExponent(long initial, int steps, int repeats) throws E {
        List<Sample> samples = new ArrayList<>();

        // JVM Warmup
        for (long n = initial, i = 0; i < steps; n = update(n), i++)
            getElapsedTimeNanos(n);

        for (long n = initial, i = 0; i < steps; n = update(n), i++) {
            long sum = 0L;
            for (int j = 0; j < repeats; j++)
                sum += getElapsedTimeNanos(n);
            samples.add(new Sample(n, (double) sum / repeats));
        }

        return exponent(samples);
    }

    private double exponent(List<Sample> samples) {
        List<Double> estimates = new ArrayList<>();
        for (int i = 1; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            Sample previous = samples.get(i - 1);

            double diffLogTime = Math.log(sample.time) - Math.log(previous.time);
            double diffLogN = Math.log(sample.n) - Math.log(previous.n);
            double logSlope = diffLogTime / diffLogN;

            estimates.add(logSlope);
        }
        estimates.sort(Double::compare);
        return estimates.get(estimates.size() / 2);
    }

    private Fit fit(List<Sample> samples, boolean amortized) {
        List<Regression> result = new ArrayList<>();
        for (OrderOfGrowth model : OrderOfGrowth.COMMON) {
            Regression fit = fit(model, samples, amortized);
            if (!Double.isNaN(fit.AIC()))
                result.add(fit);
        }

        // https://en.wikipedia.org/wiki/Relative_likelihood#Relative_likelihood_of_models
        double smallestAIC = result.stream().mapToDouble(Regression::AIC).min().orElse(0);
        double sumRelativeLikelihoods = result.stream().mapToDouble(regression ->
            Math.exp(-0.5 * (regression.AIC() - smallestAIC))
        ).sum();

        // Wagenmakers, EJ., Farrell, S. AIC model selection using Akaike weights.
        // Psychonomic Bulletin & Review 11, 192–196 (2004). https://doi.org/10.3758/BF03206482
        Map<Regression, Double> akaikeWeights = new HashMap<>();
        for (Regression model : result) {
            double relativeLikelihoodToBest = Math.exp(-0.5 * (model.AIC() - smallestAIC));
            akaikeWeights.put(model, relativeLikelihoodToBest / sumRelativeLikelihoods);
        }

        result.sort(Comparator.comparing(akaikeWeights::get));
        Regression best = result.getLast();
        Regression secondBest = result.get(result.size() - 2);

        double wBest = akaikeWeights.get(best);
        double wSecond = akaikeWeights.get(secondBest);

        double pValueAdjustment = 1.0 / (1 + Math.pow(best.fTest().pValue / 0.05, 6));

        double discrimination = wBest - wSecond;
        double fitAdequacy = best.RSquared() * pValueAdjustment;
        double similarity = OrderOfGrowth.similarity(best.model, secondBest.model);

        double confidence = discrimination * fitAdequacy;
        double ambiguity = (1 - discrimination) * (1 - similarity);

        return new Fit(best, secondBest, confidence, ambiguity);
    }

    private Regression fit(OrderOfGrowth model, List<Sample> samples, boolean amortized) {
        double[][] x = new double[samples.size()][1];
        double maxPredicted = samples.stream().mapToDouble(model::predict).max().orElse(1.0);
        for (int i = 0; i < samples.size(); i++)
            x[i][0] = model.predict(samples.get(i)) / (maxPredicted * (amortized ? samples.get(i).n : 1));

        double maxTime = samples.stream().mapToDouble(Sample::time).max().orElse(1.0);
        double[] y = new double[samples.size()];
        for (int i = 0; i < y.length; i++)
            y[i] = samples.get(i).time / (maxTime * (amortized ? samples.get(i).n : 1));

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.setNoIntercept(false);
        regression.newSampleData(y, x);

        return new Regression(model, samples, regression);
    }

    private long getElapsedTimeNanos(long input) throws E {
        T data = input(input);
        long start = System.nanoTime();
        action(data);
        long end = System.nanoTime();
        return end - start;
    }

    static void main(String[] args) throws Exception {
        OrderOfGrowthEstimator<Integer[], Exception> estimator = new OrderOfGrowthEstimator<>() {
            @Override
            protected Integer[] input(long n) {
                Integer[] a = new Integer[Math.toIntExact(n)];
                for (int i = 0; i < a.length; i++)
                    a[i] = (int) (Math.random() * n);
                return a;
            }

            @Override
            protected void action(Integer[] input) {
                double sum = 0.0;
                for (int i = 0; i < input.length; i++) {
                    for (int j = 0; j < input.length; j++) {
                        sum += i * j * input[j];
                    }
                }
            }

            @Override
            protected long update(long n) {
                return 2 * n;
            }
        };
        OrderOfGrowthEstimator.Fit fit = estimator.fit(1000L, 10, 5);
        System.out.println("T(N) ~ " + fit.best.toStringWithoutIntercept());
        System.out.println("T(N) = O(" + fit.best.model + ")");
        System.out.println("Confidence = " + fit.confidence);
        System.out.println("Ambiguity relative to O(" + fit.secondBest.model + ") = " + fit.ambiguity);
    }
}
