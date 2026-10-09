package com.sentinelqa.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Loads externalised test data from the classpath.
 *
 * <p>Keeping data out of the test body is what lets the same test method cover eight scenarios and
 * lets a non-developer add the ninth.</p>
 */
public final class JsonDataReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonDataReader() {
    }

    public static <T> T read(String resource, Class<T> type) {
        try (InputStream in = stream(resource)) {
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to parse test data: " + resource, e);
        }
    }

    public static <T> List<T> readList(String resource, Class<T> type) {
        try (InputStream in = stream(resource)) {
            return MAPPER.readValue(in,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, type));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to parse test data list: " + resource, e);
        }
    }

    public static List<Map<String, Object>> readRows(String resource) {
        try (InputStream in = stream(resource)) {
            return MAPPER.readValue(in, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Unable to parse test data rows: " + resource, e);
        }
    }

    /** Shapes JSON rows into the {@code Object[][]} a TestNG {@code @DataProvider} returns. */
    public static Object[][] asDataProvider(String resource) {
        List<Map<String, Object>> rows = readRows(resource);
        Object[][] data = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            data[i][0] = rows.get(i);
        }
        return data;
    }

    private static InputStream stream(String resource) {
        InputStream in = JsonDataReader.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Test data not found on classpath: " + resource);
        }
        return in;
    }
}
