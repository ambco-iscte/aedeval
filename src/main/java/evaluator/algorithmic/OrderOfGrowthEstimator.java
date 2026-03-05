package evaluator.algorithmic;

import org.apache.commons.math3.distribution.FDistribution;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.jspecify.annotations.NonNull;

import java.util.*;

public abstract class OrderOfGrowthEstimator<T> {

    public record Regression(OrderOfGrowth model, List<Sample> samples, OLSMultipleLinearRegression regression) {

        public record FTestResult(double statistic, double pValue) { }

        public double AIC() {
            int n = samples.size();
            return n * Math.log(RSS() / n) + 4.0;
        }

        public double BIC() {
            int n = samples.size();
            return n * Math.log(RSS() / n) + 2 * Math.log(n);
        }

        public double RSS() {
            return regression.calculateResidualSumOfSquares();
        }

        public double RSquared() {
            return regression.calculateRSquared();
        }

        public double scalar() {
            double maxPredicted = samples.stream().mapToDouble(model::predict).max().orElse(1.0);
            return maxPredicted * regression.estimateRegressionParameters()[1];
        }

        public double intercept() {
            double maxTime = samples.stream().max(Comparator.comparing(Sample::time)).map(Sample::time).orElse(1.0);
            double maxPredicted = samples.stream().mapToDouble(model::predict).max().orElse(1.0);
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
    }

    public record Fit(Regression regression, double confidence) { }
    public record Sample(long n, double time) { }

    protected abstract T input(long n);
    protected abstract void action(T input);
    protected abstract long update(long n);

    public Fit fit(long initial, int steps, int repeats) {
        List<Sample> samples = new ArrayList<>();

        // JVM Warmup
        for (long n = update(initial), i = 0; i < steps; n = update(n), i++)
            getElapsedTimeNanos(n);

        for (long n = update(initial), i = 0; i < steps; n = update(n), i++) {
            long sum = 0L;
            for (int j = 0; j < repeats; j++)
                sum += getElapsedTimeNanos(n);
            samples.add(new Sample(n, (double) sum / repeats));
        }

        return fit(samples, exponent(samples));
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

    private boolean plausible(OrderOfGrowth model, double exponent) {
        if (model == OrderOfGrowth.CONSTANT || model == OrderOfGrowth.LOGLOG || model == OrderOfGrowth.LOGARITHMIC)
            return exponent < 0.6;
        if (model == OrderOfGrowth.LINEAR || model == OrderOfGrowth.LINEARITHMIC)
            return exponent >= 0.6 && exponent < 1.6;
        if (model == OrderOfGrowth.QUADRATIC)
            return exponent >= 1.6 && exponent < 2.6;
        if (model == OrderOfGrowth.CUBIC)
            return exponent >= 2.6 && exponent < 3.6;
        if (model == OrderOfGrowth.QUARTIC)
            return exponent >= 3.6 && exponent < 4.6;
        if (model == OrderOfGrowth.QUINTIC)
            return exponent >= 4.6 && exponent < 5.6;
        return true;
    }

    private Fit fit(List<Sample> samples, double predictedExponent) {
        List<Regression> result = new ArrayList<>();
        for (OrderOfGrowth model : OrderOfGrowth.COMMON) {
            //if (plausible(model, predictedExponent))
            Regression fit = fit(model, samples);
            if (!Double.isNaN(fit.AIC()))
                result.add(fit);
        }

        // Wagenmakers, EJ., Farrell, S. AIC model selection using Akaike weights.
        // Psychonomic Bulletin & Review 11, 192–196 (2004). https://doi.org/10.3758/BF03206482
        double smallestAIC = result.stream().min(Comparator.comparing(Regression::AIC)).orElse(result.getFirst()).AIC();
        double sumRelativeLikelihoods = result.stream().mapToDouble(regression ->
            Math.exp(-0.5 * (regression.AIC() - smallestAIC))
        ).sum();
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
        double separationFromSecondBest = wBest > 0 ? (wBest - wSecond) / wBest : 0;

        double pValueGate = 1.0 / (1 + Math.pow(best.fTest().pValue / 0.05, 6));

        double confidence = wBest * separationFromSecondBest * pValueGate;

        return new Fit(best, confidence);
    }

    private Regression fit(OrderOfGrowth model, List<Sample> samples) {
        double[][] x = new double[samples.size()][1];
        double maxPredicted = samples.stream().mapToDouble(model::predict).max().orElse(1.0);
        for (int i = 0; i < samples.size(); i++)
            x[i][0] = model.predict(samples.get(i)) / maxPredicted;

        double maxTime = samples.stream().max(Comparator.comparing(Sample::time)).map(Sample::time).orElse(1.0);
        double[] y = new double[samples.size()];
        for (int i = 0; i < y.length; i++)
            y[i] = samples.get(i).time / maxTime;

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.setNoIntercept(false);
        regression.newSampleData(y, x);

        return new Regression(model, samples, regression);
    }


    private long getElapsedTimeNanos(long input) {
        T data = input(input);
        long start = System.nanoTime();
        action(data);
        long end = System.nanoTime();
        return end - start;
    }

    static void main(String[] args) {
        OrderOfGrowthEstimator<Integer[]> estimator = new OrderOfGrowthEstimator<>() {
            @Override
            protected Integer[] input(long n) {
                Integer[] a = new Integer[Math.toIntExact(n)];
                for (int i = 0; i < a.length; i++)
                    a[i] = (int) (Math.random() * n);
                return a;
            }

            @Override
            protected void action(Integer[] input) {
                Arrays.sort(input);
            }

            @Override
            protected long update(long n) {
                return 2 * n;
            }
        };
        OrderOfGrowthEstimator.Fit fit = estimator.fit(1000L, 10, 5);
        System.out.println("T(N) ~ " + fit.regression);
        System.out.println("T(N) ~ O(" + fit.regression.model + ")");
        System.out.println("Confidence = " + fit.confidence);
    }
}
