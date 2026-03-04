package evaluator.algorithmic;

import org.apache.commons.math3.distribution.FDistribution;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.util.*;

public abstract class OrderOfGrowthEstimator<T> {

    private record Regression(
        OrderOfGrowth model,
        double scalar,
        double intercept,
        OLSMultipleLinearRegression regression,
        double fTestPValue,
        double aic
    ) { }

    public record Fit(OrderOfGrowth model, double confidence) { }

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

        Optional<Sample> max = samples.stream().max(Comparator.comparing(sample -> sample.time));
        if (max.isPresent()) {
            double maxTime = max.get().time;
            samples.replaceAll(sample -> new Sample(sample.n, sample.time / maxTime));
        }

        Regression[] models = fit(samples, exponent(samples));
        return new Fit(models[0].model, score(models));
    }

    private double exponent(List<Sample> samples) {
        List<Double> estimates = new ArrayList<>();
        for (int i = 1; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            Sample previous = samples.get(i - 1);
            double exponent = (Math.log(sample.time) - Math.log(previous.time)) / (Math.log(sample.n) - Math.log(previous.n));
            estimates.add(exponent);
        }
        estimates.sort(Double::compare);
        return estimates.get(estimates.size() / 2);
    }

    private boolean plausible(OrderOfGrowth model, double exponent) {
        if (model == OrderOfGrowth.CONSTANT || model == OrderOfGrowth.LOGARITHMIC)
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

    private Regression[] fit(List<Sample> samples, double predictedExponent) {
        List<Regression> result = new ArrayList<>();
        for (OrderOfGrowth model : OrderOfGrowth.BASIS)
            if (plausible(model, predictedExponent))
                result.add(fit(model, samples));
        result.sort(Comparator.comparing(regression -> regression.aic));
        return result.toArray(new Regression[0]);
    }

    private Regression fit(OrderOfGrowth model, List<Sample> samples) {
        double[][] x = new double[samples.size()][1];
        for (int i = 0; i < samples.size(); i++) {
            x[i][0] = model.predict(samples.get(i));
        }

        double[] y = new double[samples.size()];
        for (int i = 0; i < y.length; i++)
            y[i] = samples.get(i).time;

        // Coefficients
        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.setNoIntercept(false);
        regression.newSampleData(y, x);
        double[] coefficients = regression.estimateRegressionParameters();
        double intercept = coefficients[0];
        double scalar = coefficients[1];

        // Degrees of Freedom
        int df1 = 1;
        int df2 = samples.size() - 2;

        // F-Test
        double tss = regression.calculateTotalSumOfSquares();
        double rss = regression.calculateResidualSumOfSquares();
        double statistic = ((tss - rss) / df1) / (rss / df2);
        FDistribution fDistribution = new FDistribution(df1, df2);
        double pValue = 1 - fDistribution.cumulativeProbability(statistic);

        // AIC
        double aic = samples.size() * Math.log(regression.calculateResidualSumOfSquares() / samples.size()) / 4.0;

        return new Regression(model, scalar, intercept, regression, pValue, aic);
    }

    private double score(Regression[] models) {
        if (models.length < 2)
            return 1.0;
        return 0.0; // TODO: separation, cross-validation by storing double[] times in Sample
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
        System.out.println(fit.model);
        System.out.println(fit.confidence);
    }
}
