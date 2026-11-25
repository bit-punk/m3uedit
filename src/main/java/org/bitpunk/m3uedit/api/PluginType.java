package org.bitpunk.m3uedit.api;

import java.lang.annotation.*;

/**
 * The identity of a plugin: its type id, stored as {@code type} in project-files, and the key of its name shown to
 * the user. Both are read without creating the plugin.
 * <p>
 * The id must never change once project-files have been written with it, also when the class is renamed or moved.
 * The plugins of m3uedit use the simple class-names they have been stored with so far (e.g. {@code FileInput});
 * plugins of other parties use qualified ids (e.g. {@code de.example.MyInput}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginType {

    /**
     * @return the type id of the plugin, unique among all plugins
     */
    String id();

    /**
     * @return the key of the name shown to the user in the resource bundle of the plugin ({@link #texts()}), so the
     * name is known without creating the plugin; empty if the name is only known by the plugin itself
     * ({@link Plugin#getName()})
     */
    String nameKey() default "";

    /**
     * @return the base name of the resource bundle with the texts of the plugin, e.g. {@code de.example.messages} for
     * {@code de/example/messages.properties} (English) and {@code de/example/messages_de.properties} (German), read
     * with the class loader of the plugin; see {@link PluginContext#text(String, Object...)}
     */
    String texts() default "";
}
