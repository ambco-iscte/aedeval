package evaluator;

import extensions.ProgressBar;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;

public class Runnable implements Callable<Tester> {
    private final Submission submission;
    private final Class<? extends Tester> tester;
    private final ProgressBar progress;

    public Runnable(Submission submission, Class<? extends Tester> tester, ProgressBar progress) {
        this.submission = submission;
        this.tester = tester;
        this.progress = progress;
    }

    @Override
    public Tester call() {
        try {
            Tester test = tester.getDeclaredConstructor(Submission.class).newInstance(this.submission);
            test.runAllTests();
            progress.step();
            return test;
        } catch (IOException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }
}
