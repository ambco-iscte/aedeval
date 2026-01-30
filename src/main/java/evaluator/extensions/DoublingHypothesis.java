package evaluator.extensions;

import org.apache.commons.math3.util.Pair;

public abstract class DoublingHypothesis {

    public abstract void before(int N);

    public abstract void action(int N);

    public Pair<Double, Double> run(int initial, int limit, int averages, boolean verbose) {
        double previous = getAverageElapsedTimeMillis(initial / 2, averages) / 1000.0;
        double old = previous;

        if (verbose) {
            System.out.println("Doubling Hypothesis\n");
            System.out.println("N\t\tT(N)\t\tratio\t\tlg(ratio)");
        }

        for (int n = initial; n <= limit; n += n) {
            double time = getAverageElapsedTimeMillis(n, averages) / 1000.0;
            double ratio = time / previous;
            double lgRatio = Math.log(ratio) / Math.log(2);
            if (verbose)
                System.out.printf("%d\t\t%.3f\t\t%.3f\t\t%.3f\n", n, time, ratio, lgRatio);
            old = previous;
            previous = time;
        }

        double b = Math.log(previous / old) / Math.log(2);
        double a = previous / Math.pow(limit, b);

        return new Pair<>(a, b);
    }

    private double getAverageElapsedTimeMillis(int N, int trials) {
        double avg = 0;
        for (int i = 0; i < trials; i++)
            avg += getElapsedTimeMillis(N);
        return avg / trials;
    }

    private long getElapsedTimeMillis(int N) {
        before(N);
        long start = System.currentTimeMillis();
        action(N);
        long end = System.currentTimeMillis();
        return end - start;
    }
}
