package org.bitpunk.m3uedit.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UncaughtExceptionHandlerTest {

    @Test
    void testDescribeSkipsWrappersWithoutOwnInformation() {
        IllegalStateException cause = new IllegalStateException("the real problem");
        // as thrown by FXMLLoader for exceptions in event-handlers
        RuntimeException fxmlWrapper = new RuntimeException(new InvocationTargetException(new RuntimeException(cause)));

        assertThat(UncaughtExceptionHandler.describe(fxmlWrapper)).isEqualTo("the real problem");
        assertThat(UncaughtExceptionHandler.describe(new UncheckedIOException(new IOException("disk full"))))
                .isEqualTo("disk full");
    }

    @Test
    void testDescribeKeepsExceptionsWithOwnMessage() {
        RuntimeException withMessage = new RuntimeException("Upload failed", new IOException("timeout"));
        assertThat(UncaughtExceptionHandler.describe(withMessage)).isEqualTo("Upload failed");
    }

    @Test
    void testDescribeUsesClassNameWithoutMessage() {
        assertThat(UncaughtExceptionHandler.describe(new NullPointerException())).isEqualTo("NullPointerException");
        assertThat(UncaughtExceptionHandler.describe(new RuntimeException((String) null))).isEqualTo("RuntimeException");
    }

    @Test
    void testUncaughtExceptionIsReportedOnTheFxThread() {
        List<String> reported = new ArrayList<>();
        List<Runnable> fxTasks = new ArrayList<>();
        UncaughtExceptionHandler handler = new UncaughtExceptionHandler(reported::add, fxTasks::add);

        handler.uncaughtException(Thread.currentThread(), new RuntimeException(new IllegalArgumentException("bad value")));

        assertThat(reported).isEmpty();
        fxTasks.forEach(Runnable::run);
        assertThat(reported).containsExactly("bad value");
    }

    @Test
    void testOnlyOneErrorIsReportedAtATime() {
        List<String> reported = new ArrayList<>();
        AtomicReference<UncaughtExceptionHandler> handler = new AtomicReference<>();
        handler.set(new UncaughtExceptionHandler(message -> {
            reported.add(message);
            // an error occurring while the dialog is shown (showAndWait runs a nested event-loop)
            handler.get().uncaughtException(Thread.currentThread(), new IllegalStateException("second"));
        }, Runnable::run));

        handler.get().uncaughtException(Thread.currentThread(), new IllegalStateException("first"));

        assertThat(reported).containsExactly("first");
    }

    @Test
    void testMissingToolkitDoesNotThrow() {
        UncaughtExceptionHandler handler = new UncaughtExceptionHandler(message -> {
        }, task -> {
            throw new IllegalStateException("Toolkit not initialized");
        });

        handler.uncaughtException(Thread.currentThread(), new IllegalStateException("error"));
    }
}
