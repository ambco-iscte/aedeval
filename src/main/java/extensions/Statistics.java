package extensions;

public class Statistics {

    public static <T extends Number> double sum(Iterable<T> iterable) {
        double x = 0.0;
        for (T item : iterable)
            x += item.doubleValue();
        return x;
    }

    public static <T extends Number> double mean(Iterable<T> iterable) {
        double x = 0.0;
        int count = 0;
        for (T item : iterable) {
            x += item.doubleValue();
            count++;
        }
        return x / count;
    }

    public static double mean(double[] array) {
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
        double avg = mean(iterable);
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

    public static <T extends Number> double std(Iterable<T> iterable) {
        return Math.sqrt(variance(iterable));
    }

    public static <T extends Number> double std(Iterable<T> iterable, double average) {
        return Math.sqrt(variance(iterable, average));
    }

    public static double variance(double[] array) {
        double x = 0.0;
        double avg = mean(array);
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

    public static double std(double[] array) {
        return Math.sqrt(variance(array));
    }

    public static double std(double[] array, double average) {
        return Math.sqrt(variance(array, average));
    }
}
