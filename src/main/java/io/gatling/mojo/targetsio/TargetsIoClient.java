/*
 * Copyright 2017 Stokpop Software Solutions (http://www.stokpop.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file has been added in the fork: targetsio-gatling-maven-plugin
 */
package io.gatling.mojo.targetsio;

import io.gatling.mojo.targetsio.log.Logger;
import okhttp3.*;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.util.Map;

public class TargetsIoClient extends HttpClient {

    private final String productName;
    private final String dashboardName;
    private final String testRunId;
    private final String buildResultsUrl;
    private final String productRelease;
    private final String targetsIoUrl;
    private final String rampupTimeSeconds;

    public TargetsIoClient(String productName, String dashboardName, String testRunId, String buildResultsUrl, String productRelease, String rampupTimeInSeconds, String targetsIoUrl) {
        super();
        this.productName = productName;
        this.dashboardName = dashboardName;
        this.testRunId = testRunId;
        this.buildResultsUrl = buildResultsUrl;
        this.productRelease = productRelease;
        this.rampupTimeSeconds = rampupTimeInSeconds;
        this.targetsIoUrl = targetsIoUrl;
    }

    public void injectLogger(Logger logger) {
        this.logger = logger;
    }

    public void callTargetsIoFor(Action action) {
        String json = targetsIoJson(productName, dashboardName, testRunId, buildResultsUrl, productRelease, rampupTimeSeconds);
        logger.debug(String.join(" ", "Call for", action.getName(), "endpoint:", targetsIoUrl, "with json:", json));
        try {
            String result = post(targetsIoUrl + "/running-test/" + action.getName(), json);
            logger.debug("Result: " + result);
        } catch (IOException e) {
            logger.error("Failed to call keep-alive url: " + e.getMessage());
        }
    }

    private String post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            return responseBody == null ? "null" : responseBody.string();
        }
    }

    private String targetsIoJson(String productName, String dashboardName, String testRunId, String buildResultsUrl, String productRelease, String rampupTimeSeconds) {
        return String.join("","{'testRunId':'",testRunId, "','dashboardName':'", dashboardName, "','productName':'", productName, "','productRelease':'", productRelease, "','buildResultsUrl':'", buildResultsUrl, "','rampUpPeriod':", rampupTimeSeconds, "}").replace("'", "\"");
    }

    /**
     * Call asserts for this test run.
     * @return json string such as {"meetsRequirement":true,"benchmarkResultPreviousOK":true,"benchmarkResultFixedOK":true}
     * @throws IOException when call fails
     */
    public String callCheckAsserts() throws IOException, MojoExecutionException {
        // example: https://targets-io.com/benchmarks/DASHBOARD/NIGHTLY/TEST-RUN-831
        String url = String.join("/",targetsIoUrl, "benchmarks", productName, dashboardName, testRunId);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        return getReplyForRequestWithRetries(request, MAX_RETRIES, SLEEP_IN_MILLIS);
    }

    public enum Action {
        KeepAlive("keep-alive"), End("end");

        private String name;

        Action(String name) {
            this.name = name;
        }
        public String getName() { return name; }
    }

    public static class KeepAliveRunner implements Runnable {

        private final TargetsIoClient client;

        public KeepAliveRunner(TargetsIoClient client) {
            this.client = client;
        }

        @Override
        public void run() {
            client.callTargetsIoFor(Action.KeepAlive);
        }
    }

}
