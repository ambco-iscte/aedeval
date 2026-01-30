package reflection;

import evaluator.extensions.DoublingHypothesis;
import extensions.Levenshtein;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import static extensions.Extensions.joinToString;

/**
 * Classes that inherit Reflector can access methods that facilitate useful reflection operations.
 *
 * @author Afonso Caniço
 */
public class Reflector {

    private static final long TIMEOUT_MILLISECONDS = 5000;

    private static class None {  }

    protected static None NONE;

    private static final ExecutorService HANDLER = Executors.newCachedThreadPool();

    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = new HashMap<>();

    static {
        PRIMITIVE_WRAPPERS.put(boolean.class, Boolean.class);
        PRIMITIVE_WRAPPERS.put(byte.class, Byte.class);
        PRIMITIVE_WRAPPERS.put(char.class, Character.class);
        PRIMITIVE_WRAPPERS.put(short.class, Short.class);
        PRIMITIVE_WRAPPERS.put(int.class, Integer.class);
        PRIMITIVE_WRAPPERS.put(long.class, Long.class);
        PRIMITIVE_WRAPPERS.put(float.class, Float.class);
        PRIMITIVE_WRAPPERS.put(double.class, Double.class);
    }

    /**
     * Invokes a method on a given calling instance, returning the result (or any thrown exception).
     * @param method The method to invoke.
     * @param object The object to invoke the method on.
     * @param args The arguments to pass to the method call.
     * @return The return value of the method invocation, as a general Java object.
     */
    protected Object getInvocationResult(Method method, Object object, Object... args) throws TimeoutException, InterruptedException, ExecutionException {
        method.setAccessible(true); // Can access non-public methods through Reflection magic
        MethodInvocationHandler handler = new MethodInvocationHandler(method, object, args);
        Future<?> future = HANDLER.submit(handler);

        // Blocks current class until timed out or result available
        future.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);

        return handler.getResult();
    }

    /**
     * Invokes an object's constructor, returning the resulting instance (or any thrown exception).
     * @param constructor Object constructor.
     * @param initArgs Constructor arguments.
     * @return Object instance.
     */
    protected Object getInstance(Constructor<?> constructor, Object... initArgs) throws TimeoutException, InterruptedException, ExecutionException {
        constructor.setAccessible(true);
        ObjectInstantiationHandler handler = new ObjectInstantiationHandler(constructor, initArgs);
        Future<?> future = HANDLER.submit(handler);
        future.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
        return handler.getObject();
    }

    /**
     * Invokes all methods in a collection using the specified object instance.
     * @param methods A collection of methods.
     * @param caller The object to invoke the methods on.
     */
    protected void invokeAll(Collection<Method> methods, Object caller) {
        for (Method method : methods) {
            try {
                if (method != null) method.invoke(caller);
            } catch (IllegalAccessException | InvocationTargetException ignored) { }
        }
    }

    /**
     * Finds all methods defined by a class that are tagged with a specific annotation.
     * @param type The class.
     * @param annotationType The Annotation class.
     * @return A list of all test methods, i.e. methods tagged with the MethodTest annotation.
     */
    public static List<Method> getAnnotatedMethods(Class<?> type, Class<? extends Annotation> annotationType) {
        List<Method> tests = new ArrayList<>();
        for (Method m : type.getMethods()) {
            if (m.isAnnotationPresent(annotationType))
                tests.add(m);
        }
        return tests;
    }

    /**
     * Does the class implement the interface?
     * @param type The class.
     * @param interfaceType The type of the interface.
     * @return True if the class implements the interface; False, otherwise.
     */
    protected boolean implementsInterface(Class<?> type, Class<?> interfaceType) {
        return interfaceType.isAssignableFrom(type);
    }

    /**
     * Is the specified class generic?
     * @param type The class.
     * @return True if the class or any of its
     */
    protected boolean isGeneric(Class<?> type) {
        return type.getTypeParameters().length > 0 || hasGenericSuperclass(type);
    }

    /**
     * Is the specified class or any of its superclasses generic?
     * @param type The class.
     * @return True if the class or any of its superclasses are generic; False, otherwise.
     */
    private boolean hasGenericSuperclass(Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            if (current.getGenericSuperclass() instanceof ParameterizedType) return true;
            current = current.getSuperclass();
        }
        return false;
    }

    /**
     * Finds a nested/inner class of a specified parent class. Name is case-insensitive.
     * @param parent The declaring class.
     * @param nestedClassName The name of the nested class.
     * @return The first class found whose name matches the argument.
     * @throws ClassNotFoundException If no matching class is found.
     */
    protected Class<?> getNestedClass(Class<?> parent, String nestedClassName) throws ClassNotFoundException {
        for (Class<?> cls : parent.getDeclaredClasses()) {
            if (cls.getSimpleName().equalsIgnoreCase(nestedClassName))
                return cls;
        }
        throw new ClassNotFoundException(parent.getSimpleName() + "." + nestedClassName);
    }

    /**
     * Does the parent class contain a nested class with the given name?
     * @param parent Parent class.
     * @param nestedClassName Expected name of the nested class.
     * @return True if the parent class contains a nested class with the given name. False, otherwise.
     */
    protected boolean hasNestedClass(Class<?> parent, String nestedClassName) {
        try {
            getNestedClass(parent, nestedClassName);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Finds a field in a given class.
     * @param type The class.
     * @param name The field name. Case-insensitive.
     * @return The field, if a matching field is found.
     * @throws NoSuchFieldException If no matching field is found.
     */
    protected Field getField(Class<?> type, String name) throws NoSuchFieldException {
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equalsIgnoreCase(name))
                return field;
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    /**
     * Does the class contain a field with the given name?
     * @param type Target class.
     * @param name Expected field name.
     * @return True if the parent class contains a field with the given name. False, otherwise.
     */
    protected boolean hasField(Class<?> type, String name) {
        try {
            getField(type, name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    /**
     * Does the generic type extend the given class?
     * @param type Generic parameterized type.
     * @param clazz Expected supertype of parameterized type.
     * @return True if the type extends the given class. False, otherwise.
     */
    protected boolean isGenericExtends(Type type, Class<?> clazz) {
        return type instanceof ParameterizedType p && clazz.isAssignableFrom((Class<?>) (p.getRawType()));
    }

    /**
     * Returns an attribute/field of a given class, cast to its appropriate type.
     * See also: {@link Reflector#getField(Class, String)}.
     * @param object The object instance.
     * @param name The field name.
     * @return The field, cast to its appropriate type. Essentially: T.cast({@link Reflector#getField}).
     * @throws NoSuchFieldException If no matching field is found.
     * @throws IllegalAccessException If the found field cannot be accessed.
     */
    protected Object getProperty(Object object, String name) throws NoSuchFieldException, IllegalAccessException {
        Field field = getField(object.getClass(), name);
        field.setAccessible(true);
        return field.get(object);
    }

    // Map object array to array of objects' classes
    private Class<?>[] getObjectClasses(Object... objects) {
        if (objects == null) return null;
        Class<?>[] classes = new Class<?>[objects.length];
        for (int i = 0; i < classes.length; i++)
            classes[i] = objects[i].getClass();
        return classes;
    }

    /**
     * Are all the classes in the two class arrays pair-wise equivalent?
     * See also: {@link Reflector#equivalent(Class, Class)}.
     * @param first A class array.
     * @param second Another class array.
     * @return True if the arrays are the same reference (or both null), or their lengths are the same and all classes
     * are pair-wise equivalent. False, otherwise.
     */
    private boolean allEquivalent(Class<?>[] first, Class<?>[] second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first.length != second.length) return false;

        for (int i = 0; i < first.length; i++) {
            if (!equivalent(first[i], second[i])) return false;
        }
        return true;
    }

    /**
     * Are the two classes equivalent?
     * @param first The first class.
     * @param second The second class.
     * @return True if both arguments are the same reference (or both null) or either of the classes or their wrapper
     * type(s) is assignable from the other. False, otherwise.
     */
    private boolean equivalent(Class<?> first, Class<?> second) {
        if (first == second) return true;

        if (first.isPrimitive()) first = getPrimitiveWrapperType(first);
        if (second.isPrimitive()) second = getPrimitiveWrapperType(second);

        return first.isAssignableFrom(second) || second.isAssignableFrom(first);
    }

    /**
     * Gets the Wrapper type of the specified class.
     * @param type The class.
     * @return The Wrapper type of the class, if it is primitive; The class itself, otherwise.
     */
    private Class<?> getPrimitiveWrapperType(Class<?> type) {
        if (type.isPrimitive()) return PRIMITIVE_WRAPPERS.get(type);
        return type;
    }
}
