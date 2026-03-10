package extensions.lang;

@FunctionalInterface
public interface ThrowingBiConsumer<T, R> {
    void accept(T t, R r) throws Exception;
}
