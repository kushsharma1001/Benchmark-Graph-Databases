package ai.wexa.gdbench.db;

import ai.wexa.gdbench.config.Configs;
import ai.wexa.gdbench.config.PlatformConfig;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.time.Duration;

/**
 * A thin, platform-agnostic wrapper around the official Neo4j Bolt driver.
 *
 * <p>Every target platform (CognoDB, Neo4j Aura, Memgraph, self-hosted) speaks the
 * same Bolt protocol, so a single driver — pointed at a different URI with
 * different credentials — is all that separates them. This is deliberate: it is
 * what lets the benchmark run <em>identical</em> query code against every database,
 * which is a core fairness requirement. Credentials are always resolved from the
 * environment; nothing is hard-coded.
 */
public final class GraphDatabaseClient implements AutoCloseable {

    private final PlatformConfig platform;
    private final Driver driver;

    private GraphDatabaseClient(PlatformConfig platform, Driver driver) {
        this.platform = platform;
        this.driver = driver;
    }

    /**
     * Connect to a platform using credentials from the environment. The driver's
     * connection pool is shared by every worker thread in the mixed workload.
     */
    public static GraphDatabaseClient connect(PlatformConfig platform, Configs.Credentials creds) {
        AuthToken auth = (creds.user() == null || creds.user().isBlank())
                ? AuthTokens.none()                       // Memgraph local: no auth
                : AuthTokens.basic(creds.user(), creds.password());

        Config config = Config.builder()
                // Pool big enough for the 40-client mixed workload plus headroom.
                .withMaxConnectionPoolSize(64)
                .withConnectionAcquisitionTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .withConnectionTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .withMaxTransactionRetryTime(15, java.util.concurrent.TimeUnit.SECONDS)
                .withLogging(org.neo4j.driver.Logging.slf4j())
                .build();

        Driver driver = GraphDatabase.driver(creds.uri(), auth, config);
        // Fail fast with an actionable message if the endpoint is unreachable.
        try {
            driver.verifyConnectivity();
        } catch (RuntimeException ex) {
            driver.close();
            throw new IllegalStateException(
                    "Cannot reach " + platform.displayName + " at " + creds.uri()
                            + ". Check " + platform.uriEnv() + "/" + platform.userEnv()
                            + "/" + platform.passwordEnv() + " and that the instance is running. "
                            + "Cause: " + ex.getMessage(), ex);
        }
        return new GraphDatabaseClient(platform, driver);
    }

    public PlatformConfig platform() {
        return platform;
    }

    /** A fresh session. Sessions are cheap and NOT thread-safe — one per unit of work. */
    public Session session() {
        return driver.session();
    }

    /**
     * Best-effort server version string, for the README environment table. Not all
     * engines expose the same procedure, so failures degrade to "unknown".
     */
    public String serverInfo() {
        try (Session s = session()) {
            return s.run("CALL dbms.components() YIELD name, versions, edition "
                            + "RETURN name + ' ' + versions[0] + ' (' + edition + ')' AS info")
                    .single().get("info").asString();
        } catch (RuntimeException e) {
            return "unknown (dbms.components unavailable)";
        }
    }

    @Override
    public void close() {
        driver.close();
    }

    /** Convenience for tests / callers that already hold a Duration budget. */
    public static Duration defaultTimeout() {
        return Duration.ofSeconds(30);
    }
}
