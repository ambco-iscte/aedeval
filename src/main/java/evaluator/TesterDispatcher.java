package evaluator;

import extensions.Console;
import extensions.ProgressBar;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;

public record TesterDispatcher(
    Submission submission,
    Class<? extends Tester> tester,
    String[] allowedPackages,
    ProgressBar progress
) implements Callable<Tester> {

    @Override
    public Tester call() {
        Tester test = null;
        try {
            test = tester.getConstructor(Submission.class, String[].class).newInstance(submission, allowedPackages);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            Console.error("Could not dispatch test of type " + tester.getCanonicalName() + ": " + e.getMessage());
        } catch (NoSuchMethodException e) {
            try {
                test = tester.getConstructor(Submission.class).newInstance(submission);
            }
            catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                Console.error("Could not dispatch test of type " + tester.getCanonicalName() + ": " + ex.getMessage());
            }
            catch (NoSuchMethodException ex) {
                Console.error("Could not find any constructors for tester of type " + tester.getCanonicalName() + ": " + ex.getMessage());
            }
        }

        if (test == null)
            return null;
        try {
            test.runAllTests();
            progress.step();
            return test;
        } catch (IOException e) {
            return null;
        }
    }
}
