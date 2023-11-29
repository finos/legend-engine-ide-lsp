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

package org.finos.legend.engine.ide.lsp.extension;

import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionSource;

import java.util.Objects;

public class LegendExecutionTestableSource extends LegendExecutionSource
{
    private final String testSuiteId;
    private final String testId;
    private final String assertionId;

    private LegendExecutionTestableSource(String testSuiteId, String testId, String assertionId, String entityPath, SourceType type)
    {
        super(entityPath, type, "legendExecutionTestableSource");
        this.testSuiteId = Objects.requireNonNull(testSuiteId, "testSuiteId is required");
        this.testId = Objects.requireNonNull(testId, "testId is required");
        this.assertionId = assertionId;
    }

    public  String getTestSuiteId()
    {
        return this.testSuiteId;
    }

    public String getTestId()
    {
        return this.testId;
    }

    public String getAssertionId()
    {
        return this.assertionId;
    }

    public static LegendExecutionTestableSource newTestableSource(String testSuiteId, String testId, String assertionId, String entityPath, SourceType type)
    {
        return new LegendExecutionTestableSource(testSuiteId, testId, assertionId, entityPath, type);
    }

    public static LegendExecutionTestableSource newTestableSource(String testSuiteId, String testId, String entityPath, SourceType type)
    {
        return new LegendExecutionTestableSource(testSuiteId, testId, null, entityPath, type);
    }
}
