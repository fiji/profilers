package fiji;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.Loader;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

public class SwingEDTChecker extends ProfilerBase<SwingEDTChecker> {

	private CtClass jComponentClass;
	protected Set<String> threadSafe;

	public SwingEDTChecker() {
		super(System.getenv("TEST_PROFILE_ONLY"), System.getenv("TEST_PROFILE_SKIP"));
	}

	// implemented abstract methods

	@Override
	protected void init(final ClassPool pool, final Loader loader) {
		try {
			jComponentClass = pool.get("javax.swing.JComponent");
		} catch (NotFoundException e) {
			throw new RuntimeException(e);
		}
		threadSafe = new HashSet<String>();
		threadSafe.add("repaint");
        threadSafe.add("revalidate");
        threadSafe.add("invalidate");
        threadSafe.add("getListeners");
	}

	private boolean isThreadSafe(final String methodName) {
		return threadSafe.contains(methodName) ||
				(methodName.endsWith("Listener") &&
						(methodName.startsWith("add") || methodName.endsWith("remove")));
	}

	@Override
	protected void handle(CtClass clazz, CtBehavior behavior)
			throws CannotCompileException {
		behavior.instrument(new ExprEditor() {
			@Override
			public void edit(final MethodCall call) {
				try {
					if (!call.getMethod().getDeclaringClass().subclassOf(jComponentClass) ||
							isThreadSafe(call.getMethodName())) return;
					try {
						call.replace("if ("
								+ ((call.getMethod().getModifiers() & Modifier.STATIC) != 0 ? "" : "$0.isShowing() &&")
								+ "\t\t!javax.swing.SwingUtilities.isEventDispatchThread()) {"
								+ "\tnew Exception(\"EDT violation\").printStackTrace();"
								+ "\tSystem.exit(1);"
								+ "}"
								+ "$_ = $proceed($$);");
					} catch (CannotCompileException e) {
						e.printStackTrace();
					}
				} catch (NotFoundException e) {
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	protected void report(PrintStream out) {
		// this method body intentionally left blank
	}

	// convenience methods

	public static ProfilerBase<?> getInstance() {
		return getInstance(SwingEDTChecker.class);
	}

	public static boolean startProfiling(String mainClass, String... args) {
		return startProfiling(SwingEDTChecker.class, mainClass, args);
	}

	public static void setActive(final boolean active) {
		setActive(SwingEDTChecker.class, active);
	}

	protected static void main(final String... args) throws Throwable {
		main(SwingEDTChecker.class, args);
	}

	protected static boolean isActive() {
		return isActive(SwingEDTChecker.class);
	}

	protected static void report(final File file) throws FileNotFoundException {
		report(SwingEDTChecker.class, file);
	}
}
