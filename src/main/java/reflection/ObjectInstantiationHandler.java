package reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;

/**
 * Used asynchronously to compute constructor calls separately to avoid infinite waiting time when the call
 * results in an infinite loop.

 * @author Afonso Caniço
 */
public class ObjectInstantiationHandler implements Runnable {

    private final Constructor<?> constructor;

    private final Object[] initArgs;

    private Object object = null;

    public ObjectInstantiationHandler(Constructor<?> constructor, Object[] initArgs) {
        this.constructor = constructor;
        this.initArgs = initArgs;
    }

    public Object getObject() throws ExecutionException {
        if (object instanceof Throwable exception)
            throw new ExecutionException(exception);
        return object;
    }

    @Override
    public void run() {
        try {
            object = constructor.newInstance(initArgs);
        } catch (InvocationTargetException e) {
            object = e.getTargetException();
        } catch (Exception e) {
            object = e;
        }
    }
}
