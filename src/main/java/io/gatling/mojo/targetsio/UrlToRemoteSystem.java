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

import org.apache.maven.plugin.MojoExecutionException;

import java.util.regex.Pattern;

/**
 * The url to call to a remote system after the test.
 * Has a test run start and end time to be included, see per example.
 *
 * Example: http://domain.com/path/something?startTime=${testStartTime}&endTime=${testEndTime}&ticket=XXXX&token=YYYY
 */
public class UrlToRemoteSystem {
    private static final Pattern PLACEHOLDER_TEST_START_TIME = Pattern.compile("\\{\\{testStartTime}}");
    private static final Pattern PLACEHOLDER_TEST_END_TIME = Pattern.compile("\\{\\{testEndTime}}");
    private String urlToRemoteSystem;

    public UrlToRemoteSystem(String urlWithPlaceHolders, TestRunClock testRunClock) throws MojoExecutionException {
        if (urlWithPlaceHolders == null) {
            throw new MojoExecutionException(String.format("The remote system url is null."));
        }
        if (!PLACEHOLDER_TEST_START_TIME.matcher(urlWithPlaceHolders).find()) {
           throw new MojoExecutionException(String.format("Placeholder [%s] for test start time in missing in remote system url [%s]", PLACEHOLDER_TEST_START_TIME, urlWithPlaceHolders));
        }
        if (!PLACEHOLDER_TEST_END_TIME.matcher(urlWithPlaceHolders).find()) {
           throw new MojoExecutionException(String.format("Placeholder [%s] for test start time in missing in remote system url [%s]", PLACEHOLDER_TEST_END_TIME, urlWithPlaceHolders));
        }
        final String testStartTime = String.valueOf(testRunClock.getTestStartTime());
        final String testEndTime = String.valueOf(testRunClock.getTestEndTime());
        this.urlToRemoteSystem = PLACEHOLDER_TEST_END_TIME.matcher(
                PLACEHOLDER_TEST_START_TIME.matcher(urlWithPlaceHolders).replaceAll(testStartTime))
                .replaceAll(testEndTime);
    }

    public String urlBasedOnTestRunClock() {
        return urlToRemoteSystem;
    }

    @Override
    public String toString() {
        return "UrlToRemoteSystem{" +
                "urlToRemoteSystem='" + urlToRemoteSystem + '\'' +
                '}';
    }
}
