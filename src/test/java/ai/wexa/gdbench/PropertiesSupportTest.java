package ai.wexa.gdbench;

import ai.wexa.gdbench.config.PropertiesSupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@code ${NAME:default}} placeholder resolver. We invoke the
 * package-private {@code resolve} via reflection so the test can live in the
 * public test package alongside the others.
 */
class PropertiesSupportTest {

    private static String resolve(String raw) throws Exception {
        Method m = PropertiesSupport.class.getDeclaredMethod("resolve", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, raw);
    }

    @Test
    void literalDefaultIsUsedWhenNothingSupplied() throws Exception {
        // No env var / system property named FOO_URI_XYZ exists → default applies.
        assertEquals("bolt://localhost:7687",
                resolve("${GDBENCH_TEST_MISSING_URI:bolt://localhost:7687}"));
    }

    @Test
    void systemPropertyOverridesDefault() throws Exception {
        String key = "GDBENCH_TEST_OVERRIDE";
        System.setProperty(key, "bolt://from-sysprop:7687");
        try {
            assertEquals("bolt://from-sysprop:7687",
                    resolve("${" + key + ":bolt://default:7687}"));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void placeholderWithNoDefaultAndNoValueResolvesToNull() throws Exception {
        // This is the security contract: a missing secret with no default is
        // "unset" (null), so the caller skips the platform instead of using "".
        assertNull(resolve("${GDBENCH_TEST_SECRET_ABSENT}"));
    }

    @Test
    void emptyExplicitDefaultResolvesToEmptyString() throws Exception {
        // ${NAME:} means "default to empty" — distinct from "unset".
        assertEquals("", resolve("${GDBENCH_TEST_EMPTY_DEFAULT:}"));
    }

    @Test
    void embeddedPlaceholderKeepsSurroundingText() throws Exception {
        assertTrue(resolve("prefix-${GDBENCH_TEST_MISSING:mid}-suffix")
                .equals("prefix-mid-suffix"));
    }

    @Test
    void plainValuePassesThroughUnchanged() throws Exception {
        assertEquals("neo4j", resolve("neo4j"));
    }
}
