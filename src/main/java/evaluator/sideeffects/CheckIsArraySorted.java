package evaluator.sideeffects;

import evaluator.Tester;
import extensions.Extensions;

public class CheckIsArraySorted<T extends Comparable<? super T>> implements Tester.SideEffectChecker {

    private final T[] array;
    private final boolean ascending;

    public CheckIsArraySorted(T[] array) {
        this.array = array;
        this.ascending = true;
    }

    public CheckIsArraySorted(T[] array, boolean ascending) {
        this.array = array;
        this.ascending = ascending;
    }

    @Override
    public String message(boolean success) {
        String order = ascending ? "ascending" : "descending";
        String isSorted = success ? "sorted" : "not sorted";
        return "the array " + Extensions.toStringOrDefault(array) + " is " + isSorted + " in " + order + " order.";
    }

    @Override
    public boolean check() {
        for (int i = 0; i < array.length - 1; i++) {
            if (array[i + 1].compareTo(array[i]) < 0)
                return false;
        }
        return true;
    }
}
