package ai.wexa.gdbench.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * One platform entry from {@code config/platforms.yaml}.
 *
 * <p>Credentials are intentionally NOT part of this object — they are read from
 * environment variables named by {@link #envPrefix} ({@code <PREFIX>_URI},
 * {@code <PREFIX>_USER}, {@code <PREFIX>_PASSWORD}). This keeps every secret out
 * of source control (an explicit rule in the assignment).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PlatformConfig {

    public String id;
    public String displayName;
    public String envPrefix;
    public String tier;
    public String engine;
    public Map<String, String> specs;
    public String notes;

    /** The env var name that holds the Bolt URI for this platform. */
    public String uriEnv()      { return envPrefix + "_URI"; }
    public String userEnv()     { return envPrefix + "_USER"; }
    public String passwordEnv() { return envPrefix + "_PASSWORD"; }

    /** Root object of platforms.yaml. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Registry {
        public List<PlatformConfig> platforms;
    }
}
