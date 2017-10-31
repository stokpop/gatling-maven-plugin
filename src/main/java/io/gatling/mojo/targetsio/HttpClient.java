package io.gatling.mojo.targetsio;

import io.gatling.mojo.targetsio.log.Logger;
import io.gatling.mojo.targetsio.log.SystemOutLogger;
import okhttp3.*;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;

public abstract class HttpClient {

    final int MAX_RETRIES = 10;
    final long SLEEP_IN_MILLIS = 3000;

    static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    final OkHttpClient client = new OkHttpClient();
    Logger logger = new SystemOutLogger();

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
                    if (response.code() == 200) {
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
}
