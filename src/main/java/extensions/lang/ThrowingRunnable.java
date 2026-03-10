package extensions.lang;

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Exception;
}
