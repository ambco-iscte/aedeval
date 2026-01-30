package extensions;

import evaluator.Submission;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class Extensions {

    public static <T extends Comparable<? super T>> boolean isSorted(Iterable<T> iterable) {
        Iterator<T> iterator = iterable.iterator();

        if (!iterator.hasNext())
            return true;

        // 1 Element
        T current = iterator.next();
        if (!iterator.hasNext())
            return true;

        // >= 2 Elements
        T previous = current;
        current = iterator.next();
        while (iterator.hasNext()) {
            if (current.compareTo(previous) < 0)
                return false;
            previous = current;
            current = iterator.next();
        }
        return current.compareTo(previous) >= 0;
    }

    public static <T> List<T> copy(Iterable<T> iterable) {
        List<T> list = new ArrayList<>();
        for (T item : iterable) {
            list.add(item);
        }
        return list;
    }

    public static <T> boolean hasNoDuplicates(Iterable<T> sorted) {
        Iterator<T> iterator = sorted.iterator();

        if (!iterator.hasNext())
            return true;

        // 1 Element
        T current = iterator.next();
        if (!iterator.hasNext())
            return true;

        // >= 2 Elements
        T previous = current;
        current = iterator.next();
        while (iterator.hasNext()) {
            if (Objects.equals(current, previous))
                return false;
            previous = current;
            current = iterator.next();
        }
        return !Objects.equals(current, previous);
    }

    public static <T> boolean hasNoDuplicates(T[] array) {
        for (int i = 0; i < array.length - 1; i++) {
            if (Objects.equals(array[i], array[i + 1]))
                return false;
        }
        return true;
    }

    public static <T extends Comparable<? super T>> List<T> sorted(Collection<T> collection) {
        List<T> sorted = new ArrayList<>(collection);
        Collections.sort(sorted);
        return sorted;
    }

    public static <T extends Comparable<? super T>, R extends T> boolean isPermutation(Collection<T> first, Collection<R> second) {
        if (first.size() != second.size())
            return false;

        List<T> a = sorted(first);
        List<R> b = sorted(second);

        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i), b.get(i)))
                return false;
        }
        return true;
    }

    public static <T extends Comparable<? super T>> boolean isSorted(T[] array) {
        for (int i = 0; i < array.length - 1; i++) {
            if (array[i].compareTo(array[i + 1]) > 0)
                return false;
        }
        return true;
    }

    public static <T> String toString(Iterable<T> iterable) {
        Iterator<T> iterator = iterable.iterator();

        // Empty Iterable
        if (!iterator.hasNext())
            return "[]";

        // 1 Element
        Object current = iterator.next();
        if (!iterator.hasNext())
            return "[" + Extensions.toStringOrDefault(current) + "]";

        // >= 2 Elements
        StringBuilder str = new StringBuilder("[" + Extensions.toStringOrDefault(current));
        while (iterator.hasNext()) {
            current = iterator.next();
            str.append(", ").append(Extensions.toStringOrDefault(current));
        }
        return str.append("]").toString();
    }

    private static <T> String declaration(TypeVariable<Class<T>> type) {
        Type[] bounds = type.getBounds();
        String name = type.getName();
        if (bounds.length != 0) {
            name += " extends " + joinToString(bounds, Type::getTypeName);
        }
        return name;
    }

    public static <T> String declaration(Class<T> type) {
        TypeVariable<Class<T>>[] parameters = type.getTypeParameters();
        Class<?>[] interfaces = type.getInterfaces();

        String dec = type.getSimpleName();

        if (parameters.length != 0)
            dec += "<" + joinToString(parameters, Extensions::declaration) + ">";

        if (interfaces.length != 0)
            dec += " implements " + joinToString("implements ", interfaces, Extensions::declaration);

        return dec;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(Iterable<T> iterable) {
        List<T> list = new LinkedList<>();
        for (T item : iterable)
            list.add(item);
        return list.toArray((T[]) new Object[list.size()]);
    }

    public static <T> boolean match(Iterable<T> a, Iterable<T> b) {
        return Arrays.equals(toArray(a), toArray(b));
    }

    public static <T> boolean match(Iterable<T> a, T[] b) {
        return Arrays.equals(toArray(a), b);
    }

    public static double round(double x, int places) {
        double mul = Math.pow(10, places);
        return Math.round(x * mul) / mul;
    }

    public static <T> int countIf(Iterable<T> iterable, Predicate<T> predicate) {
        int count = 0;
        for (T item : iterable) {
            if (predicate.test(item))
                count++;
        }
        return count;
    }

    public static <T> String joinToString(String delimiter, Iterable<T> elements) {
        Iterator<?> iterator = elements.iterator();
        if (iterator.hasNext()) {
            StringBuilder str = new StringBuilder(Objects.toString(iterator.next()));
            while (iterator.hasNext())
                str.append(delimiter).append(iterator.next());
            return str.toString().trim();
        }
        return "";
    }

    public static <T> String joinToString(Iterable<T> elements) {
        return Extensions.joinToString(", ", elements);
    }

    public static <T> String joinToString(String delimiter, T[] elements) {
        return Extensions.joinToString(delimiter, Arrays.asList(elements));
    }

    public static <T> String joinToString(T[] elements) {
        return Extensions.joinToString(Arrays.asList(elements));
    }

    public static <T, R> String joinToString(String delimiter, Iterable<T> elements, Function<T, R> map) {
        Iterator<T> iterator = elements.iterator();
        if (iterator.hasNext()) {
            StringBuilder str = new StringBuilder(Objects.toString(map.apply(iterator.next())));
            while (iterator.hasNext())
                str.append(delimiter).append(map.apply(iterator.next()));
            return str.toString().trim();
        }
        return "";
    }

    public static <T, R> String joinToString(Iterable<T> elements, Function<T, R> map) {
        return joinToString(", ", elements, map);
    }

    public static <T, R> String joinToString(String delimiter, T[] elements, Function<T, R> map) {
        return joinToString(delimiter, Arrays.asList(elements), map);
    }

    public static <T, R> String joinToString(T[] elements, Function<T, R> map) {
        return joinToString(", ", elements, map);
    }

    @SafeVarargs
    public static <T extends Comparable<? super T>> T min(T... values) {
        T min = null;
        for (T value : values) {
            if (min == null || value.compareTo(min) < 0)
                min = value;
        }
        return min;
    }

    public static Object[] toArray(Object o) {
        if (o == null)
            return null;

        if (Iterable.class.isAssignableFrom(o.getClass()))
            return toArray((Iterable<?>) o);
        if (o.getClass().isArray())
            return (Object[]) o;

        throw new IllegalArgumentException("Cannot convert object of type " + o.getClass() + " to an array!");
    }

    public static String toStringOrDefault(Object o) {
        try {
            if (o == null)
                return "null";

            if (o.getClass().isArray())
                return "[" + joinToString((Object[]) o, Extensions::toStringOrDefault) + "]";
            else if (Iterable.class.isAssignableFrom(o.getClass()))
                return "[" + joinToString((Iterable<?>) o, Extensions::toStringOrDefault) + "]";

            return Objects.toString(o);
        } catch (Throwable e1) {
            try {
                return stringify(o);
            } catch (Throwable e2) {
                return o.getClass().getSimpleName() + "@" + System.identityHashCode(o);
            }
        }
    }

    public static String stringify(Object object) {
        if (object == null)
            return "null";

        Class<?> type = object.getClass();

        if (type.isPrimitive() || String.class.isAssignableFrom(type))
            return toStringOrDefault(object);

        if (type.isArray())
            return "[" + joinToString((Object[]) object, Extensions::stringify) + "]";

        if (object instanceof File)
            return ((File) object).getPath();

        StringBuilder attributes = new StringBuilder();
        Field[] fields = type.getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()))
                continue;
            field.trySetAccessible();
            try {
                Object value = field.get(object);
                if (value != object)
                    attributes.append(field.getName()).append("=").append(stringify(value)).append(", ");
            } catch (IllegalAccessException ignored) {
                attributes.append(field.getName()).append(", ");
            }
        }

        String str = type.getSimpleName() + "[" + attributes;
        return str.substring(0, str.length() - 2) + "]";
    }

    public static Integer[] randomIntArray(int length, int min, int max) {
        Integer[] array = new Integer[length];
        for (int i = 0; i < length;  i++) {
            array[i] = (int) (min + (max - min) * Math.random());
        }
        return array;
    }
}
