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
