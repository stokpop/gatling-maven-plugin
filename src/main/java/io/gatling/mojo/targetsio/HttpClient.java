package io.gatling.mojo.targetsio;

import io.gatling.mojo.targetsio.log.Logger;
import io.gatling.mojo.targetsio.log.SystemOutLogger;
import okhttp3.*;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class HttpClient {

    final int MAX_RETRIES = 10;
    final long SLEEP_IN_MILLIS = 3000;

    static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    final OkHttpClient client = new OkHttpClient();
    Logger logger = new SystemOutLogger();

    private Map<String, String> headers;

    HttpClient() {
        this(Collections.emptyMap());
    }

    HttpClient(Map<String, String> headers) {
        this.headers = headers;
    }

    public void injectLogger(Logger logger) {
        this.logger = logger;
    }

    String getReplyForRequestWithRetries(final Request request, final int maxRetries, final long sleepInMillis) throws MojoExecutionException {
        String replyBody = null;
        int retries = 0;
        while (retries <= maxRetries) {
            try {
                try (Response response = client.newCall(request).execute()) {

                    ResponseBody responseBody = response.body();
                    if (isHttpSuccessCode(response.code())) {
                        replyBody = responseBody == null ? "null" : responseBody.string();
                        break;
                    } else {
                        String message = responseBody == null ? response.message() : responseBody.string();
                        logger.warn("Failed to call url [" + request.url() + "] code [" + response.code() + "] retry [" + retries + "/" + maxRetries + "] " + message);
                    }
                    try {
                        Thread.sleep(sleepInMillis);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            } catch (IOException e) {
                logger.warn("An exception happened trying to retrieve url [" + request.url() + "]: " + e.getMessage());
            }
            retries = retries + 1;
            if (retries == maxRetries) {
                throw new MojoExecutionException("Max retries reached: unable to retrieve url [" + request.url() + "]");
            }
        }
        return replyBody;
    }

    private static boolean isHttpSuccessCode(int code) {
        return code >= 200 && code < 300;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Parse http headers into Map.
     * @param headers a comma separated list of name:value pairs
     * @return unmodifiable Map with header name-value pairs
     */
    public static Map<String, String> parseHeaders(String headers) {
        if (headers == null) { return Collections.emptyMap(); }
        return Collections.unmodifiableMap(Arrays.stream(headers.split(","))
                .map(pair -> pair.split(":"))
                .collect(Collectors.toMap(nameValue -> nameValue[0], nameValue -> nameValue[1])));
    }
}
