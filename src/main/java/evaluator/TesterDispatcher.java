package evaluator;

import extensions.out.Console;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;

public record TesterDispatcher(
    Submission submission,
    Class<? extends Tester> tester,
    double fileNameSimilarityThreshold
) implements Callable<Tester> {

    @Override
    public Tester call() {
        Tester test = null;
        try {
            test = tester.getConstructor(Submission.class).newInstance(submission);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            Console.error("Could not dispatch test of type " + tester.getCanonicalName() + ": " + e.getMessage());
        } catch (NoSuchMethodException e) {
            Console.error("Could not find any constructors for tester of type " + tester.getCanonicalName() + ": " + e.getMessage());
        }

        if (test == null)
            return null;
        try {
            test.setFileNameSimilarityThreshold(fileNameSimilarityThreshold);
            test.runAllTests();
            return test;
        } catch (IOException e) {
            return null;
        }
    }
}
