package evaluator.sideeffects;

import evaluator.Tester;
import extensions.Extensions;

import static reflection.Reflector.async;

public class CheckIterableIsSorted<T extends Comparable<? super T>> implements Tester.SideEffectChecker {

    private final Iterable<T> iterable;
    private final boolean ascending;

    public CheckIterableIsSorted(Iterable<T> iterable, boolean ascending) {
        this.iterable = iterable;
        this.ascending = ascending;
    }

    public CheckIterableIsSorted(Iterable<T> iterable) {
        this.iterable = iterable;
        this.ascending = true;
    }

    @Override
    public String message(boolean success) {
        String order = ascending ? "ascending" : "descending";
        String isSorted = success ? "sorted" : "not sorted (but it should be)";
        return "the " + iterable.getClass().getSimpleName() + " " + Extensions.toStringOrDefault(iterable) + " is " + isSorted + " in " + order + " order";
    }

    @Override
    public boolean check() throws Exception {
        return async(() -> Extensions.isSorted(iterable, ascending));
    }
}
