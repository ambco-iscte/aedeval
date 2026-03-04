package evaluator.algorithmic;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;

public class Memory {

    private static final int OVERHEAD_REFERENCE = 8;

    private static final int OVERHEAD_ARRAY = 24;

    private static final int OVERHEAD_OBJECT = 16;

    private static final Map<Class<?>, Integer> BYTES_PRIMITIVE = Map.ofEntries(
            new SimpleEntry<>(Boolean.class, Byte.BYTES),
            new SimpleEntry<>(boolean.class, Byte.BYTES),
            new SimpleEntry<>(Byte.class, Byte.BYTES),
            new SimpleEntry<>(byte.class, Byte.BYTES),
            new SimpleEntry<>(Character.class, Character.BYTES),
            new SimpleEntry<>(char.class, Character.BYTES),
            new SimpleEntry<>(Integer.class, Integer.BYTES),
            new SimpleEntry<>(int.class, Integer.BYTES),
            new SimpleEntry<>(Float.class, Float.BYTES),
            new SimpleEntry<>(float.class, Float.BYTES),
            new SimpleEntry<>(Long.class, Long.BYTES),
            new SimpleEntry<>(long.class, Long.BYTES),
            new SimpleEntry<>(Double.class, Double.BYTES),
            new SimpleEntry<>(double.class, Double.BYTES)
    );

    public static int shallow(Class<?> type) {
        int memory = OVERHEAD_OBJECT;
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isAbstract(field.getModifiers()))
                memory += BYTES_PRIMITIVE.getOrDefault(field.getType(), OVERHEAD_REFERENCE);
        }
        return (memory + 7) & (-8); // Round to next multiple of 8
    }
}
