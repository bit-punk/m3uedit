package org.bitpunk.m3uedit.util;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Handles exceptions not caught anywhere else (e.g. in event-handlers): they are logged and shown to the user.
 * <p>
 * Only one error dialog is shown at a time, so a repeating error does not flood the screen with dialogs.
 */
public class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(UncaughtExceptionHandler.class);

    private final Consumer<String> errorReporter;
    private final Consumer<Runnable> fxExecutor;
    private final AtomicBoolean reporting = new AtomicBoolean();

    public UncaughtExceptionHandler() {
        this(DialogUtil::showUnexpectedError, Platform::runLater);
    }

    /**
     * @param errorReporter shows a message to the user
     * @param fxExecutor    runs the given task on the JavaFX application thread
     */
    UncaughtExceptionHandler(Consumer<String> errorReporter, Consumer<Runnable> fxExecutor) {
        this.errorReporter = errorReporter;
        this.fxExecutor = fxExecutor;
    }

    /**
     * Installs this handler for all threads, including the current (JavaFX application) thread.
     */
    public void install() {
        Thread.setDefaultUncaughtExceptionHandler(this);
        Thread.currentThread().setUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        log.error("Uncaught exception in thread {}", thread.getName(), throwable);
        String message = describe(throwable);
        try {
            // deferred: dialogs must not be shown while JavaFX is processing a layout or an animation
            fxExecutor.accept(() -> report(message));
        } catch (IllegalStateException e) {
            log.warn("Cannot show error dialog, JavaFX is not running");
        }
    }

    private void report(String message) {
        if (!reporting.compareAndSet(false, true)) {
            log.debug("an error dialog is already shown, not showing: {}", message);
            return;
        }
        try {
            errorReporter.accept(message);
        } finally {
            reporting.set(false);
        }
    }

    /**
     * Returns a user readable description of the given error. Wrapping exceptions without information of their own
     * (e.g. the {@link InvocationTargetException} of an fxml event-handler) are skipped.
     */
    public static String describe(Throwable throwable) {
        Throwable cause = rootOfWrappers(throwable);
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static Throwable rootOfWrappers(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current && isWrapper(current)) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean isWrapper(Throwable throwable) {
        return throwable instanceof InvocationTargetException
                || throwable instanceof UncheckedIOException
                // e.g. "new RuntimeException(cause)", whose message is just the cause's toString()
                || (throwable.getClass() == RuntimeException.class
                && (throwable.getMessage() == null || throwable.getMessage().equals(throwable.getCause().toString())));
    }
}
