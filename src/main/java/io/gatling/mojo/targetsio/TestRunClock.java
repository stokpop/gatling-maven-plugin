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

public class TestRunClock {
    private static final int TIMESTAMP_NOT_SET = -1;

    private long testStartTime;
    private long testEndTime;

    public TestRunClock(long testStartTime) {
        this.testStartTime = testStartTime;
        this.testEndTime = TIMESTAMP_NOT_SET;
    }

    public TestRunClock(long testStartTime, long testEndTime) {
        this.testStartTime = testStartTime;
        this.testEndTime = testEndTime;
    }

    public long getTestStartTime() {
        return testStartTime;
    }

    public void setTestStartTime(long testStartTime) {
        this.testStartTime = testStartTime;
    }

    public long getTestEndTime() {
        return testEndTime;
    }

    public void setTestEndTime(long testEndTime) {
        this.testEndTime = testEndTime;
    }

}
