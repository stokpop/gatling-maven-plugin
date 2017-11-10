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