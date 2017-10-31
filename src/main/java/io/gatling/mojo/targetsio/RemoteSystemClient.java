package io.gatling.mojo.targetsio;

import okhttp3.Request;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.util.Map;

public class RemoteSystemClient extends HttpClient {

    public RemoteSystemClient() {
        super();
    }

    public RemoteSystemClient(Map<String, String> headers) {
       super(headers);
    }

    /**
     * Call remote system using the UrlToRemoteSystem object.
     * @return json string such as {"meetsRequirement":true,"benchmarkResultPreviousOK":true,"benchmarkResultFixedOK":true}
     * @throws MojoExecutionException when call fails
     */
    public String call(UrlToRemoteSystem urlToRemoteSystem) throws MojoExecutionException {

        final String url = urlToRemoteSystem.urlBasedOnTestRunClock();

        final Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .get();
        getHeaders().forEach(requestBuilder::addHeader);
        final Request request = requestBuilder.build();

        return getReplyForRequestWithRetries(request, MAX_RETRIES, SLEEP_IN_MILLIS);
    }
}
