package evaluator.sideeffects;

import evaluator.Tester;
import extensions.Extensions;

import static reflection.Reflector.async;

public class CheckArrayIsSorted<T extends Comparable<? super T>> implements Tester.SideEffectChecker {

    private final T[] array;
    private final boolean ascending;

    public CheckArrayIsSorted(T[] array) {
        this.array = array;
        this.ascending = true;
    }

    public CheckArrayIsSorted(T[] array, boolean ascending) {
        this.array = array;
        this.ascending = ascending;
    }

    @Override
    public String message(boolean success) {
        String order = ascending ? "ascending" : "descending";
        String isSorted = success ? "sorted" : "not sorted";
        return "the array " + Extensions.toStringOrDefault(array) + " is " + isSorted + " in " + order + " order";
    }

    @Override
    public boolean check() throws Exception {
        return async(() -> Extensions.isSorted(array, ascending));
    }
}
