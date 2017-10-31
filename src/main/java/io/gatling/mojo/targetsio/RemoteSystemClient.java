package io.gatling.mojo.targetsio;

import okhttp3.Request;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;

public class RemoteSystemClient extends HttpClient {

    public RemoteSystemClient() {}

    /**
     * Call remote system using the UrlToRemoteSystem object.
     * @return json string such as {"meetsRequirement":true,"benchmarkResultPreviousOK":true,"benchmarkResultFixedOK":true}
     * @throws MojoExecutionException when call fails
     */
    public String call(UrlToRemoteSystem urlToRemoteSystem) throws MojoExecutionException {

        final String url = urlToRemoteSystem.urlBasedOnTestRunClock();

        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        return getReplyForRequestWithRetries(request, MAX_RETRIES, SLEEP_IN_MILLIS);
    }
}
