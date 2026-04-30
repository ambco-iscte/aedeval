package evaluator;

import extensions.Extensions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public enum AssertEqualsType {
    EXACT,          // Result should equal this exact value
    ANY,            // Result should equal any of the values in an array/Iterable
    PERMUTATION,    // Result should be a permutation of the given array/Iterable
    CONTENT,        // Result should be an array/Iterable with the same content as the given array/Iterable,
    NOTNULL,        // Result should be non-null
    TYPEOF;          // Result should be of a given type

    public boolean equals(Object expected, Object actual) {
        switch (this) {
            case EXACT -> {
                return Objects.equals(expected, actual);
            }

            case ANY -> {
                Object[] exp = Extensions.toArray(expected);
                return Arrays.asList(exp).contains(actual);
            }

            case PERMUTATION -> {
                if (actual == null)
                    return expected == null;
                if (!actual.getClass().isArray() && !Iterable.class.isAssignableFrom(actual.getClass()))
                    return false;

                List<Object> exp = Arrays.asList(Extensions.toArray(expected));
                List<Object> act = Arrays.asList(Extensions.toArray(actual));
                return exp.size() == act.size() && new HashSet<>(exp).equals(new HashSet<>(act));
            }

            case CONTENT -> {
                if (actual == null)
                    return expected == null;
                if (!actual.getClass().isArray() && !Iterable.class.isAssignableFrom(actual.getClass()))
                    return false;

                return Arrays.equals(Extensions.toArray(expected), Extensions.toArray(actual));
            }

            case NOTNULL -> {
                return actual != null;
            }

            case TYPEOF -> {
                return actual != null && ((Class<?>) expected).isAssignableFrom(actual.getClass());
            }

            default -> {
                return false;
            }
        }
    }
}
