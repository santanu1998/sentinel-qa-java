package com.sentinelqa.db;

import com.sentinelqa.config.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists every test result to a relational store so run history becomes queryable.
 *
 * <p>A run report tells you what failed today. This table tells you what has been failing for three
 * weeks — which is the difference between chasing a flaky test and fixing it. The schema is plain
 * SQL and runs on H2 locally or on the team's MySQL/PostgreSQL instance by changing one URL.</p>
 */
public final class ExecutionRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionRepository.class);

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS test_execution (
                id             INT AUTO_INCREMENT PRIMARY KEY,
                run_id         VARCHAR(64)  NOT NULL,
                suite_name     VARCHAR(128) NOT NULL,
                test_class     VARCHAR(256) NOT NULL,
                test_name      VARCHAR(256) NOT NULL,
                layer          VARCHAR(16)  NOT NULL,
                environment    VARCHAR(32)  NOT NULL,
                browser        VARCHAR(32),
                status         VARCHAR(16)  NOT NULL,
                duration_ms    BIGINT       NOT NULL,
                triage_bucket  VARCHAR(32),
                failure_reason VARCHAR(1024),
                defect_key     VARCHAR(32),
                executed_at    TIMESTAMP    NOT NULL
            )
            """;

    private static final String INSERT = """
            INSERT INTO test_execution
                (run_id, suite_name, test_class, test_name, layer, environment, browser,
                 status, duration_ms, triage_bucket, failure_reason, defect_key, executed_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    /** Tests that both passed and failed inside the same run window: the flaky set. */
    private static final String FLAKY_QUERY = """
            SELECT test_class, test_name,
                   COUNT(*)                                        AS executions,
                   SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failures,
                   ROUND(100.0 * SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) / COUNT(*), 2) AS failure_pct
            FROM test_execution
            GROUP BY test_class, test_name
            HAVING SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) > 0
               AND SUM(CASE WHEN status = 'PASSED' THEN 1 ELSE 0 END) > 0
            ORDER BY failure_pct DESC
            """;

    private ExecutionRepository() {
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                ConfigLoader.get("db.url"),
                ConfigLoader.get("db.user"),
                ConfigLoader.get("db.password"));
    }

    public static void initialise() {
        if (!ConfigLoader.getBoolean("db.enabled")) {
            return;
        }
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute(DDL);
            LOG.info("Execution analytics schema ready at {}", ConfigLoader.get("db.url"));
        } catch (SQLException e) {
            LOG.warn("Could not initialise execution store; analytics disabled for this run", e);
        }
    }

    public static void record(ExecutionRecord r) {
        if (!ConfigLoader.getBoolean("db.enabled")) {
            return;
        }
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(INSERT)) {
            ps.setString(1, r.runId());
            ps.setString(2, r.suiteName());
            ps.setString(3, r.testClass());
            ps.setString(4, r.testName());
            ps.setString(5, r.layer());
            ps.setString(6, r.environment());
            ps.setString(7, r.browser());
            ps.setString(8, r.status());
            ps.setLong(9, r.durationMs());
            ps.setString(10, r.triageBucket());
            ps.setString(11, truncate(r.failureReason()));
            ps.setString(12, r.defectKey());
            ps.setTimestamp(13, Timestamp.from(r.executedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warn("Failed to persist execution record for {}", r.testName(), e);
        }
    }

    public static List<FlakyTest> flakyTests() {
        List<FlakyTest> results = new ArrayList<>();
        if (!ConfigLoader.getBoolean("db.enabled")) {
            return results;
        }
        try (Connection c = connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(FLAKY_QUERY)) {
            while (rs.next()) {
                results.add(new FlakyTest(
                        rs.getString("test_class"),
                        rs.getString("test_name"),
                        rs.getInt("executions"),
                        rs.getInt("failures"),
                        rs.getDouble("failure_pct")));
            }
        } catch (SQLException e) {
            LOG.warn("Flakiness query failed", e);
        }
        return results;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    public record ExecutionRecord(String runId, String suiteName, String testClass, String testName,
                                  String layer, String environment, String browser, String status,
                                  long durationMs, String triageBucket, String failureReason,
                                  String defectKey, Instant executedAt) {
    }

    public record FlakyTest(String testClass, String testName, int executions, int failures,
                            double failurePercentage) {
    }
}
