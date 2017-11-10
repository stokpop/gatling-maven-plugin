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

public interface Logger {
    void info(String message);
    void warn(String message);
    void error(String message);
    void debug(String message);
}
