/*
 * Copyright 2024 Goldman Sachs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.legend.engine.ide.lsp.extension.test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult.Type;

public class LegendTestExecutionResult
{
    private final String id;
    private final Type type;
    private final String message;
    private final String output;
    private final List<LegendTestAssertionResult> assertionResults;

    private LegendTestExecutionResult(String id, Type type, String message, String output, List<LegendTestAssertionResult> assertionResults)
    {
        this.id = Objects.requireNonNull(id, "testId is required");
        this.type = Objects.requireNonNull(type, "result type is required");
        this.message = message;
        this.output = output;
        this.assertionResults = Collections.unmodifiableList(assertionResults);
    }

    public String getId()
    {
        return id;
    }

    public Type getType()
    {
        return type;
    }

    public String getMessage()
    {
        return message;
    }

    public String getOutput()
    {
        return output;
    }

    public List<LegendTestAssertionResult> getAssertionResults()
    {
        return assertionResults;
    }

    @Override
    public String toString()
    {
        return "LegendTestExecutionResult{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", message='" + message + '\'' +
                ", output='" + output + '\'' +
                ", assertionResults=" + assertionResults +
                '}';
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof LegendTestExecutionResult))
        {
            return false;
        }
        LegendTestExecutionResult that = (LegendTestExecutionResult) o;
        return Objects.equals(id, that.id) && type == that.type && Objects.equals(message, that.message) && Objects.equals(output, that.output) && Objects.equals(assertionResults, that.assertionResults);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, type, message, output, assertionResults);
    }

    public static LegendTestExecutionResult error(Exception t, String... testId)
    {
        StringWriter writer = new StringWriter();
        try (PrintWriter pw = new PrintWriter(writer))
        {
            t.printStackTrace(pw);
        }
        return new LegendTestExecutionResult(String.join(".", testId), Type.ERROR, "Error", writer.toString(), List.of());
    }

    public static LegendTestExecutionResult error(String message, String... testId)
    {
        return new LegendTestExecutionResult(String.join(".", testId), Type.ERROR, "Error", message, List.of());
    }

    public static LegendTestExecutionResult unknown(String message, String... testId)
    {
        return new LegendTestExecutionResult(String.join(".", testId), Type.WARNING, "Unknown Result", message, List.of());
    }

    public static LegendTestExecutionResult success(String... testId)
    {
        return new LegendTestExecutionResult(String.join(".", testId), Type.SUCCESS, null, null, List.of());
    }

    public static LegendTestExecutionResult failures(List<LegendTestAssertionResult> assertionResults, String... testId)
    {
        return new LegendTestExecutionResult(String.join(".", testId), Type.FAILURE, null, null, assertionResults);
    }
}
