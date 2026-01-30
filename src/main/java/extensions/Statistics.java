package extensions;

import java.util.Map;

public class Statistics {

    public static <T extends Number> double sum(Iterable<T> iterable) {
        double x = 0.0;
        for (T item : iterable)
            x += item.doubleValue();
        return x;
    }

    public static <T extends Number> double average(Iterable<T> iterable) {
        double x = 0.0;
        int count = 0;
        for (T item : iterable) {
            x += item.doubleValue();
            count++;
        }
        return x / count;
    }

    public static double average(double[] array) {
        double x = 0.0;
        int count = 0;
        for (double t : array) {
            x += t;
            count++;
        }
        return x / count;
    }

    public static <T extends Number> double variance(Iterable<T> iterable) {
        double x = 0.0;
        double avg = average(iterable);
        int count = 0;
        for (T item : iterable) {
            double xi = item.doubleValue();
            x += (xi - avg) * (xi - avg);
            count++;
        }
        return x / count;
    }

    public static <T extends Number> double variance(Iterable<T> iterable, double average) {
        double x = 0.0;
        int count = 0;
        for (T item : iterable) {
            double xi = item.doubleValue();
            x += (xi - average) * (xi - average);
            count++;
        }
        return x / count;
    }

    public static <T extends Number> double getStandardDeviation(Iterable<T> iterable) {
        return Math.sqrt(variance(iterable));
    }

    public static <T extends Number> double getStandardDeviation(Iterable<T> iterable, double average) {
        return Math.sqrt(variance(iterable, average));
    }

    public static double variance(double[] array) {
        double x = 0.0;
        double avg = average(array);
        int count = 0;
        for (double item : array) {
            x += (item - avg) * (item - avg);
            count++;
        }
        return x / count;
    }

    public static double variance(double[] array, double average) {
        double x = 0.0;
        int count = 0;
        for (double item : array) {
            x += (item - average) * (item - average);
            count++;
        }
        return x / count;
    }

    public static double getStandardDeviation(double[] array) {
        return Math.sqrt(variance(array));
    }

    public static double getStandardDeviation(double[] array, double average) {
        return Math.sqrt(variance(array, average));
    }
}
