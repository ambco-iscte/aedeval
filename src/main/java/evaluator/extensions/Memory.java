package evaluator.extensions;

import java.lang.reflect.Field;
import java.util.Map;

import java.util.AbstractMap.SimpleEntry;

public class Memory {

    private static final int OVERHEAD_REFERENCE = 8;

    private static final int OVERHEAD_ARRAY = 24;

    private static final int OVERHEAD_OBJECT = 16;

    private static final Map<Class<?>, Integer> BYTES_PRIMITIVE = Map.ofEntries(
            new SimpleEntry<>(Boolean.class, 1),
            new SimpleEntry<>(boolean.class, 1),
            new SimpleEntry<>(Byte.class, 1),
            new SimpleEntry<>(byte.class, 1),
            new SimpleEntry<>(Character.class, 2),
            new SimpleEntry<>(char.class, 2),
            new SimpleEntry<>(Integer.class, 4),
            new SimpleEntry<>(int.class, 4),
            new SimpleEntry<>(Float.class, 4),
            new SimpleEntry<>(float.class, 4),
            new SimpleEntry<>(Long.class, 8),
            new SimpleEntry<>(long.class, 8),
            new SimpleEntry<>(Double.class, 8),
            new SimpleEntry<>(double.class, 8)
    );

    public static int shallow(Class<?> type) {
        int memory = OVERHEAD_OBJECT;
        for (Field field : type.getDeclaredFields())
            memory += BYTES_PRIMITIVE.getOrDefault(field.getType(), OVERHEAD_REFERENCE);
        return (memory + 7) & (-8); // Round to next multiple of 8
    }
}
