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

package org.finos.legend.engine.ide.lsp.extension;

public interface LegendMessageTracer extends LegendLSPFeature
{
    void trace(TraceEvent event);

    static TraceEvent event(String method, String paramsAsJson, long elapsed, Integer errorCode, String errorMessage)
    {
        return new TraceEvent(method, paramsAsJson, elapsed, errorCode, errorMessage);
    }

    class TraceEvent
    {
        private final String method;
        private final String paramsAsJson;
        private final long elapsed;
        private final Integer errorCode;
        private final String errorMessage;

        private TraceEvent(String method, String paramsAsJson, long elapsed, Integer errorCode, String errorMessage)
        {
            this.method = method;
            this.paramsAsJson = paramsAsJson;
            this.elapsed = elapsed;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public String getMethod()
        {
            return method;
        }

        public String getParamsAsJson()
        {
            return paramsAsJson;
        }

        public long getElapsed()
        {
            return elapsed;
        }

        public Integer getErrorCode()
        {
            return errorCode;
        }

        public String getErrorMessage()
        {
            return errorMessage;
        }
    }
}
