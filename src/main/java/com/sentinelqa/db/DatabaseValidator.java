package com.sentinelqa.db;

import com.sentinelqa.config.ConfigLoader;
import io.qameta.allure.Step;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Back-end assertion helper for tests that must prove an API call actually changed persisted state.
 *
 * <p>A 200 response is not evidence that a row was written. Where the team owns the database, this
 * runs the SQL that closes that gap.</p>
 */
public final class DatabaseValidator implements AutoCloseable {

    private final Connection connection;

    public DatabaseValidator() {
        this(ConfigLoader.get("db.url"), ConfigLoader.get("db.user"), ConfigLoader.get("db.password"));
    }

    public DatabaseValidator(String url, String user, String password) {
        try {
            this.connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to open validation connection to " + url, e);
        }
    }

    @Step("Run validation query")
    public List<Map<String, Object>> query(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return toRows(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Validation query failed: " + sql, e);
        }
    }

    public long count(String sql, Object... params) {
        List<Map<String, Object>> rows = query(sql, params);
        if (rows.isEmpty()) {
            return 0L;
        }
        Object value = rows.get(0).values().iterator().next();
        return ((Number) value).longValue();
    }

    public boolean exists(String sql, Object... params) {
        return !query(sql, params).isEmpty();
    }

    public int execute(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Statement failed: " + sql, e);
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    private static List<Map<String, Object>> toRows(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns; i++) {
                row.put(meta.getColumnLabel(i).toLowerCase(), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // nothing actionable at teardown
        }
    }
}
