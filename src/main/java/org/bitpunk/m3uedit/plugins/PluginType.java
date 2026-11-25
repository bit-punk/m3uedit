package org.bitpunk.m3uedit.plugins;

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
     * @return the key of the name shown to the user (see {@link org.bitpunk.m3uedit.i18n.Messages}); empty if the
     * name is only known by the plugin itself ({@link Plugin#getName()})
     */
    String nameKey() default "";
}
