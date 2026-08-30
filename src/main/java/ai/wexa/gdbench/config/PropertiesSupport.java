package ai.wexa.gdbench.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional Spring-style {@code application.properties} loader.
 *
 * <p>This harness is not a Spring application, so {@code ${NAME:default}}
 * placeholders are not resolved for us. This helper does the same job Spring's
 * {@code PropertySourcesPlaceholderConfigurer} would: it reads a properties
 * file and, for each entry, resolves the value against — in order —
 * <ol>
 *   <li>the process environment ({@code System.getenv})</li>
 *   <li>JVM system properties ({@code -D...})</li>
 *   <li>the literal default after the colon in {@code ${NAME:default}}</li>
 * </ol>
 * Resolved values are published as <em>system properties</em>. That is all it
 * takes to wire into the rest of the app: {@link Configs} already reads
 * credentials via env-var-then-system-property, so a value placed here is
 * picked up with no further change.
 *
 * <p>Security: a {@code ${NAME}} with no default and no supplied value is left
 * UNRESOLVED and not published, so the corresponding platform is skipped rather
 * than run with a bogus value — the same "blank means skip" contract as .env.
 * Real secrets are therefore never required to live in the file.
 */
public final class PropertiesSupport {

    /** System property (or env var) naming an explicit properties file to load. */
    public static final String LOCATION_KEY = "gdbench.properties";
    /** Default location checked when {@link #LOCATION_KEY} is not set. */
    public static final String DEFAULT_LOCATION = "config/application.properties";

    // Matches ${NAME} and ${NAME:default} (default may be empty or contain colons).
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?}");

    private PropertiesSupport() {
    }

    /**
     * Load the properties file if present and publish resolved values as system
     * properties (never overwriting a system property that is already set, so an
     * explicit {@code -Dkey=...} always wins). No-op when the file is absent.
     */
    public static void loadIfPresent() {
        Path location = resolveLocation();
        if (location == null || !Files.isRegularFile(location)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(location)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("WARN: could not read " + location + ": " + e.getMessage());
            return;
        }
        for (String key : props.stringPropertyNames()) {
            String resolved = resolve(props.getProperty(key));
            // Leave unresolved placeholders (missing secret, no default) unset:
            // the platform will be skipped rather than run with a bad value.
            if (resolved == null) {
                continue;
            }
            // An explicit -Dkey / real env var always takes precedence.
            if (System.getProperty(key) == null && System.getenv(key) == null) {
                System.setProperty(key, resolved);
            }
        }
    }

    private static Path resolveLocation() {
        String explicit = System.getProperty(LOCATION_KEY);
        if (explicit == null) {
            explicit = System.getenv(LOCATION_KEY);
        }
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        Path def = Path.of(DEFAULT_LOCATION);
        return Files.isRegularFile(def) ? def : null;
    }

    /**
     * Resolve every {@code ${NAME:default}} placeholder in a value.
     *
     * @return the resolved string, or {@code null} if the value is a single
     *         placeholder with no default and no supplied value (i.e. "unset").
     */
    static String resolve(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(raw);
        StringBuilder out = new StringBuilder();
        boolean sawUnresolved = false;
        while (m.find()) {
            String name = m.group(1).trim();
            String dflt = m.group(2); // null when no colon was present
            String value = lookup(name);
            if (value == null) {
                value = dflt; // may itself be "" (explicit empty default)
            }
            if (value == null) {
                sawUnresolved = true;
                value = ""; // drop the placeholder from the output
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        String result = out.toString();
        // If the whole value was one unresolved, default-less placeholder, treat
        // it as "unset" so the caller does not publish an empty credential.
        if (sawUnresolved && result.isEmpty()) {
            return null;
        }
        return result;
    }

    private static String lookup(String name) {
        String v = System.getenv(name);
        if (v == null) {
            v = System.getProperty(name);
        }
        return (v == null || v.isEmpty()) ? null : v;
    }
}
