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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The result of executing a {@link LegendCommand}.
 */
public class LegendExecutionResult
{
    private final List<String> ids;
    private final Type type;
    private final String message;
    private final String logMessage;

    private LegendExecutionResult(List<String> ids, Type type, String message, String logMessage)
    {
        Objects.requireNonNull(ids, "ids may not be null").forEach(id -> Objects.requireNonNull(id, "id may not be null"));
        if (ids.isEmpty())
        {
            throw new IllegalArgumentException("ids may not be empty");
        }
        this.ids = ids;
        this.type = Objects.requireNonNull(type, "type is required");
        this.message = Objects.requireNonNull(message, "message is required");
        this.logMessage = logMessage;
    }

    public static LegendExecutionResult errorResult(Throwable t, String message, String entityPath)
    {
        StringWriter writer = new StringWriter();
        try (PrintWriter pw = new PrintWriter(writer))
        {
            t.printStackTrace(pw);
        }
        String resultMessage;
        if (message != null)
        {
            resultMessage = message;
        }
        else
        {
            String tMessage = t.getMessage();
            resultMessage = (tMessage == null) ? "Error" : tMessage;
        }
        return LegendExecutionResult.newResult(entityPath, Type.ERROR, resultMessage, writer.toString());
    }

    /**
     * Get the result ids. These should be understood hierarchically. For example, the list could consist of an entity
     * path, a test suite id, and a test id.
     *
     * @return result ids
     */
    public List<String> getIds()
    {
        return this.ids;
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

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof LegendExecutionResult))
        {
            return false;
        }

        LegendExecutionResult that = (LegendExecutionResult) other;
        return (this.type == that.type) &&
                this.message.equals(that.message) &&
                Objects.equals(this.logMessage, that.logMessage) &&
                this.ids.equals(that.ids);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.ids, this.type, this.message, this.logMessage);
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName()).append('{');
        if (!this.ids.isEmpty())
        {
            int start = builder.length();
            this.ids.forEach(id -> ((builder.length() == start) ? builder.append("ids=[\"") : builder.append("\", \"")).append(id));
            builder.append("] ");
        }
        return builder.append("type=").append(this.type).append('}').toString();
    }

    /**
     * Construct a new Legend execution result.
     *
     * @param ids        result ids
     * @param type       result type
     * @param message    result message
     * @param logMessage log message (optional)
     * @return execution result
     */
    public static LegendExecutionResult newResult(List<String> ids, Type type, String message, String logMessage)
    {
        return new LegendExecutionResult(ids, type, message, logMessage);
    }

    /**
     * Construct a new Legend execution result.
     *
     * @param id         result id
     * @param type       result type
     * @param message    result message
     * @param logMessage log message (optional)
     * @return execution result
     */
    public static LegendExecutionResult newResult(String id, Type type, String message, String logMessage)
    {
        return newResult(Collections.singletonList(id), type, message, logMessage);
    }

    /**
     * Construct a new Legend execution result.
     *
     * @param ids     result ids
     * @param type    result type
     * @param message result message
     * @return execution result
     */
    public static LegendExecutionResult newResult(List<String> ids, Type type, String message)
    {
        return newResult(ids, type, message, null);
    }

    /**
     * Construct a new Legend execution result.
     *
     * @param id      result id
     * @param type    result type
     * @param message result message
     * @return execution result
     */
    public static LegendExecutionResult newResult(String id, Type type, String message)
    {
        return newResult(id, type, message, null);
    }

    public enum Type
    {
        SUCCESS, FAILURE, WARNING, ERROR
    }
}
