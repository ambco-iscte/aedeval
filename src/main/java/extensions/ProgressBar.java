package extensions;

import java.util.concurrent.atomic.AtomicInteger;

public class ProgressBar {

    private final int total;

    private final int length;

    private final String title;

    private final char fill;

    private final int precision;

    private final AtomicInteger step = new AtomicInteger(0);

    public ProgressBar(int total, int length, String title) {
        this.total = total;
        this.length = length;
        this.title = title;
        this.fill = '█';
        this.precision = 2;
    }

    public ProgressBar(int total, int length, String title, char fill, int precision) {
        this.total = total;
        this.length = length;
        this.title = title;
        this.fill = fill;
        this.precision = precision;
    }

    public void step() {
        step(null);
    }

    public void step(String description) {
        if (step.getAndIncrement() >= total)
            throw new IllegalStateException("");
        String percentage = String.format("%." + precision + "f", 100 * (step.get()) / (double) total);
        int filled = (step.get() * length) / total;
        String bar = String.valueOf(fill).repeat(filled) + "-".repeat(length - filled);
        if (description == null)
            System.out.printf("\r%s [%s] %s%s", title, bar, percentage, "%");
        else
            System.out.printf("\r%s [%s] %s%s %s", title, bar, percentage, "%", description);
        if (step.get() == total)
            System.out.println();
    }
}
