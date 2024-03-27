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

import java.util.Objects;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;

public class LegendTestAssertionResult
{
    private final String assertId;
    private final LegendExecutionResult.Type type;
    private final String message;
    private final String expected;
    private final String actual;
    private final TextLocation location;

    public LegendTestAssertionResult(String assertId, LegendExecutionResult.Type type, String message, String expected, String actual, TextLocation location)
    {
        this.assertId = Objects.requireNonNull(assertId, "assertId is required");
        this.type = Objects.requireNonNull(type, "type is required");
        this.location = location;
        this.message = message;
        this.expected = expected;
        this.actual = actual;
    }

    public String getAssertId()
    {
        return assertId;
    }

    public LegendExecutionResult.Type getType()
    {
        return type;
    }

    public String getMessage()
    {
        return message;
    }

    public String getExpected()
    {
        return expected;
    }

    public String getActual()
    {
        return actual;
    }

    public TextLocation getLocation()
    {
        return location;
    }

    @Override
    public String toString()
    {
        return "LegendTestAssertionResult{" +
                "assertId='" + assertId + '\'' +
                ", type=" + type +
                ", message='" + message + '\'' +
                ", expected='" + expected + '\'' +
                ", actual='" + actual + '\'' +
                ", location=" + location +
                '}';
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof LegendTestAssertionResult))
        {
            return false;
        }
        LegendTestAssertionResult that = (LegendTestAssertionResult) o;
        return Objects.equals(assertId, that.assertId) && type == that.type && Objects.equals(message, that.message) && Objects.equals(expected, that.expected) && Objects.equals(actual, that.actual) && Objects.equals(location, that.location);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(assertId, type, message, expected, actual, location);
    }

    public static LegendTestAssertionResult failure(String assertionId, TextLocation location, String message, String expected, String actual)
    {
        return new LegendTestAssertionResult(assertionId, LegendExecutionResult.Type.FAILURE, message, expected, actual, location);
    }

    public static LegendTestAssertionResult unknown(String assertionId, String message, TextLocation location)
    {
        return new LegendTestAssertionResult(assertionId, LegendExecutionResult.Type.WARNING, message, null, null, location);
    }
}
