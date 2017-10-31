package io.gatling.mojo.targetsio.log;

public class SystemOutLogger implements Logger {
    public void info(String message) {
        System.out.println("INFO:  " + message);
    }

    public void warn(String message) {
        System.out.println("WARN:  " + message);
    }

    public void error(String message) {
        System.out.println("ERROR: " + message);
    }

    public void debug(String message) {
        System.out.println("DEBUG: " + message);
    }
}