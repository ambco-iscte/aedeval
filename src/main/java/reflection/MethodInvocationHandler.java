package reflection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;

/**
 * Used asynchronously to compute method executions separately to avoid infinite waiting time when a method call
 * results in an infinite loop.
 *
 * @author Caroline Conti
 * @author Afonso Caniço
 */
public final class MethodInvocationHandler implements Runnable {
	private final Method method;
	private final Object object;
	private Object result = null;
	private final Object[] args;

	public MethodInvocationHandler(Method method, Object object, Object... args) {
		this.method = method;
		this.object = object;
		this.args = args;
	}

	public Object getResult() throws ExecutionException {
		if (result instanceof Throwable exception)
			throw new ExecutionException(exception);
		return result;
	}

	@Override
	public void run() {
		try {
			result = method.invoke(object, args);
		} catch (InvocationTargetException e) {
			result = e.getTargetException();
		} catch (Exception e) {
			result = e;
		}
	}
}