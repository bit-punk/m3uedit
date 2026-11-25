package org.bitpunk.m3uedit.i18n;

import javafx.fxml.FXMLLoader;

import java.net.URL;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * The texts of the user interface, read from {@code i18n/messages*.properties}.
 * <p>
 * English unless {@link #use(Locale)} is called when the user interface is started, so the command-line mode, the
 * log and the tests stay in English.
 * <p>
 * Texts with arguments ({0}, {1}, …) are formatted by {@link MessageFormat}: an apostrophe is written as two
 * apostrophes there ({@code ''}); texts without arguments are used as they are, also in FXML files ({@code %key}).
 */
public final class Messages {
    public static final String BUNDLE_NAME = "i18n.messages";

    private static volatile ResourceBundle bundle = load(Locale.ENGLISH);

    private Messages() {
        // Utility class
    }

    static ResourceBundle load(Locale locale) {
        // without falling back to the locale of the system: a language without bundle gets the English texts
        return ResourceBundle.getBundle(BUNDLE_NAME, locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    /**
     * Uses the texts of the given language from now on; called once at startup.
     */
    public static void use(Locale locale) {
        bundle = load(Objects.requireNonNull(locale));
    }

    public static ResourceBundle bundle() {
        return bundle;
    }

    /**
     * @return the locale of the texts, e.g. to format dates
     */
    public static Locale locale() {
        Locale locale = bundle.getLocale();
        return locale == null || locale.getLanguage().isEmpty() ? Locale.ENGLISH : locale;
    }

    /**
     * @return the text of the key, formatted with the arguments if there are any; the key itself if it is missing
     */
    public static String get(String key, Object... arguments) {
        String text;
        try {
            text = bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
        return arguments.length == 0 ? text : new MessageFormat(text, locale()).format(arguments);
    }

    /**
     * @return a loader of the FXML file resolving its {@code %key} references
     */
    public static FXMLLoader fxmlLoader(URL fxmlFile) {
        return new FXMLLoader(fxmlFile, bundle);
    }
}
