// Copyright 2023 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.engine.ide.lsp.extension.execution;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The result of executing a {@link LegendCommand}.
 */
public class LegendExecutionResult
{
    private final Type type;
    private final String message;
    private final String logMessage;
    private final List<String> sourceIds;

    private LegendExecutionResult(List<String> sourceIds, Type type, String message, String logMessage)
    {
        this.sourceIds = Objects.requireNonNull(sourceIds, "sourceIds is required");
        this.type = Objects.requireNonNull(type, "type is required");
        this.message = Objects.requireNonNull(message, "message is required");
        this.logMessage = logMessage;
    }

    /**
     * Return the type of the result: success, failure, warning, or error.
     *
     * @return result type
     */
    public Type getType()
    {
        return this.type;
    }

    /**
     * Return the result message.
     *
     * @return result message
     */
    public String getMessage()
    {
        return this.message;
    }

    /**
     * Return an optional log message.
     *
     * @return log message or null
     */
    public String getLogMessage()
    {
        return this.logMessage;
    }

    public List<String> getSourceIds()
    {
        return this.sourceIds;
    }

    /**
     * Return the log message if present. If not present, then the result message will be returned if
     * {@code returnMessageIfAbsent} is true and null if it is false.
     *
     * @param returnMessageIfAbsent whether to return the result message if the log message is absent
     * @return log message, result message, or null
     */
    public String getLogMessage(boolean returnMessageIfAbsent)
    {
        return ((this.logMessage == null) && returnMessageIfAbsent) ? this.message : this.logMessage;
    }

    /**
     * Construct a new Legend execution result.
     *
     * @param sourceIds     result sourceIds
     * @param type       result type
     * @param message    result message
     * @param logMessage log message (optional)
     * @return execution result
     */
    public static LegendExecutionResult newResult(List<String> sourceIds, Type type, String message, String logMessage)
    {
        return new LegendExecutionResult(sourceIds, type, message, logMessage);
    }

    /**
     * Construct a new Legend execution result.
     *
     * @param entityPath  result entityPath
     * @param type       result type
     * @param message    result message
     * @param logMessage log message (optional)
     * @return execution result
     */
    public static LegendExecutionResult newResult(String entityPath, Type type, String message, String logMessage)
    {
        return new LegendExecutionResult(Collections.singletonList(entityPath), type, message, logMessage);
    }

    /**
     * Construct a new Legend execution result.
     *
     * @param sourceIds result sourceIds
     * @param type    result type
     * @param message result message
     * @return execution result
     */
    public static LegendExecutionResult newResult(List<String> sourceIds, Type type, String message)
    {
        return newResult(sourceIds, type, message, null);
    }

    /**
     * Construct a new Legend execution result.
     *
     * @param entityPath  result entityPath
     * @param type    result type
     * @param message result message
     * @return execution result
     */
    public static LegendExecutionResult newResult(String entityPath, Type type, String message)
    {
        return newResult(Collections.singletonList(entityPath), type, message, null);
    }

    public enum Type
    {
        SUCCESS, FAILURE, WARNING, ERROR
    }
}
