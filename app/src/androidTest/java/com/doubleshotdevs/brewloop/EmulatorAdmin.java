package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Test-only administrator access to the local Firebase emulators.
 *
 * <p>The emulators accept the token {@code owner} as a full administrator that bypasses
 * security rules, in the same way the shop owner uses the Firebase Console. Tests use it to
 * seed records that the app itself is not allowed to write, such as staff profiles, menu
 * items, or deliberately malformed data, and to clean up afterwards.</p>
 *
 * <p>Every request goes to the emulator host and the {@code demo-brewloop} project only.
 * Each call first checks that the app is the emulator build of a demo project, so these
 * helpers can never reach the shared project.</p>
 */
final class EmulatorAdmin {

    private static final String PROJECT_ID = "demo-brewloop";
    /** Database namespace from the app's demo database URL, where the rules apply. */
    private static final String DATABASE_NAMESPACE = PROJECT_ID + "-default-rtdb";
    private static final String DATABASE_BASE = "http://10.0.2.2:9000/";
    private static final String AUTH_BASE = "http://10.0.2.2:9099/";
    private static final int TIMEOUT_MILLIS = 15_000;

    private EmulatorAdmin() {
        // Static helpers only.
    }

    /**
     * Stores JSON at a database path, bypassing security rules.
     *
     * @param path a path such as {@code Users/abc}
     * @param json the JSON value to store
     * @throws IOException if the emulator rejects the request
     */
    static void put(@NonNull String path, @NonNull String json) throws IOException {
        databaseRequest("PUT", path, json);
    }

    /**
     * Reads the JSON stored at a database path, bypassing security rules.
     *
     * @param path a path such as {@code Orders/abc}
     * @return the JSON text, {@code "null"} if nothing is stored
     * @throws IOException if the emulator rejects the request
     */
    @NonNull
    static String get(@NonNull String path) throws IOException {
        return databaseRequest("GET", path, null);
    }

    /**
     * Reads a JSON object stored at a database path.
     *
     * @param path a path such as {@code Orders/abc}
     * @return the object, or {@code null} if nothing is stored
     * @throws Exception if the emulator rejects the request or the value is not an object
     */
    @Nullable
    static JSONObject getObject(@NonNull String path) throws Exception {
        String json = get(path);
        return "null".equals(json.trim()) ? null : new JSONObject(json);
    }

    /**
     * Deletes a database path, bypassing security rules.
     *
     * @param path a path such as {@code Orders/abc}
     * @throws IOException if the emulator rejects the request
     */
    static void delete(@NonNull String path) throws IOException {
        databaseRequest("DELETE", path, null);
    }

    /**
     * Deletes one Authentication emulator account.
     *
     * @param uid the account's UID
     * @throws IOException if the emulator rejects the request
     */
    static void deleteAccount(@NonNull String uid) throws IOException {
        requireDemoEmulator();
        String body = "{\"localId\":" + JSONObject.quote(uid) + "}";
        send("POST", AUTH_BASE + "identitytoolkit.googleapis.com/v1/projects/" + PROJECT_ID
                + "/accounts:delete", body);
    }

    /**
     * Sends a database request.
     *
     * @param method the HTTP method
     * @param path   the database path
     * @param body   the request body, or {@code null}
     * @return the response body
     * @throws IOException if the emulator rejects the request
     */
    @NonNull
    private static String databaseRequest(@NonNull String method, @NonNull String path,
                                          @Nullable String body) throws IOException {
        requireDemoEmulator();
        return send(method, DATABASE_BASE + path + ".json?ns=" + DATABASE_NAMESPACE, body);
    }

    /**
     * Sends one administrator request to an emulator.
     *
     * @param method the HTTP method
     * @param url    the emulator URL
     * @param body   the request body, or {@code null}
     * @return the response body
     * @throws IOException if the emulator returns an error status
     */
    @NonNull
    private static String send(@NonNull String method, @NonNull String url,
                               @Nullable String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("Authorization", "Bearer owner");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
            String response = stream == null ? "" : readAll(stream);
            if (code >= 400) {
                throw new IOException(method + " failed with HTTP " + code + ": " + response);
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Reads a whole response stream as UTF-8 text.
     *
     * @param stream the stream
     * @return the text
     * @throws IOException if reading fails
     */
    @NonNull
    private static String readAll(@NonNull InputStream stream) throws IOException {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        }
    }

    /** Fails unless the app is the emulator build of the demo project. */
    private static void requireDemoEmulator() {
        assertTrue("Administrator access is for the emulator build only",
                FirebaseEnvironment.isLocalTestMode());
        FirebaseOptions options = FirebaseApp.getInstance().getOptions();
        assertTrue("Refusing administrator access to a non-demo project",
                PROJECT_ID.equals(options.getProjectId()));
    }
}
