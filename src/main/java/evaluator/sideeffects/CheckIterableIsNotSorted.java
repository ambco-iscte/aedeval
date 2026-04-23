package evaluator.sideeffects;

import evaluator.Tester;
import extensions.Extensions;

import static reflection.Reflector.async;

public class CheckIterableIsNotSorted<T extends Comparable<? super T>> implements Tester.SideEffectChecker {

    private final Iterable<T> iterable;

    public CheckIterableIsNotSorted(Iterable<T> iterable) {
        this.iterable = iterable;
    }

    @Override
    public String message(boolean success) {
        String isSorted = success ? "not sorted" : "sorted (but it shouldn't be)";
        return "the " + iterable.getClass().getSimpleName() + " " + Extensions.toStringOrDefault(iterable) + " is " + isSorted;
    }

    @Override
    public boolean check() throws Exception {
        return !async(() -> Extensions.isSorted(iterable, true) || Extensions.isSorted(iterable, false));
    }
}