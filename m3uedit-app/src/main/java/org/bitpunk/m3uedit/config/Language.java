package org.bitpunk.m3uedit.config;

import java.util.Locale;

/**
 * The language of the user interface.
 */
public enum Language {
    /**
     * The language of the operating system, English if it is not supported.
     */
    SYSTEM(null),
    ENGLISH(Locale.ENGLISH),
    GERMAN(Locale.GERMAN);

    private final Locale locale;

    Language(Locale locale) {
        this.locale = locale;
    }

    /**
     * @param systemLocale the locale of the operating system
     * @return the locale of the user interface: the one of the language, or the one of the system if it is supported
     */
    public Locale locale(Locale systemLocale) {
        if (locale != null) {
            return locale;
        }
        return GERMAN.locale.getLanguage().equals(systemLocale.getLanguage()) ? systemLocale : Locale.ENGLISH;
    }
}
