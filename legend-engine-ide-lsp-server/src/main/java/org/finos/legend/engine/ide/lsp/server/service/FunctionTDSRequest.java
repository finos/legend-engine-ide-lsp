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

package org.finos.legend.engine.ide.lsp.server.service;

import com.google.gson.annotations.JsonAdapter;
import java.util.Map;
import java.util.Objects;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSRequest;
import org.finos.legend.engine.ide.lsp.server.gson.LegendTypeAdapterFactory;

public class FunctionTDSRequest
{
    private final String uri;
    private final int sectionNum;
    private final String entity;
    @JsonAdapter(LegendTypeAdapterFactory.class)
    private TDSRequest request;
    private Map<String, Object> inputParameters;

    public FunctionTDSRequest(String uri, int sectionNum, String entity, TDSRequest request, Map<String, Object> inputParameters)
    {
        this.uri = Objects.requireNonNull(uri);
        this.sectionNum = Objects.requireNonNull(sectionNum);
        this.entity = Objects.requireNonNull(entity);
        this.request = Objects.requireNonNull(request);
        this.inputParameters = inputParameters;
    }

    /**
     * Return the uri of the file entity is present
     *
     * @return uri
     */
    public String getUri()
    {
        return this.uri;
    }

    /**
     * Return the entity for which we are doing server side operations
     *
     * @return entity
     */
    public String getEntity()
    {
        return this.entity;
    }

    /**
     * Return the sectionNum of the entity.
     *
     * @return sectionNum
     */
    public int getSectionNum()
    {
        return this.sectionNum;
    }

    /**
     * Return the request sent by ag-grid for the custom query.
     *
     * @return request
     */
    public TDSRequest getRequest()
    {
        return request;
    }

    /**
     * Return the input parameter values of the function.
     *
     * @return inputParameters
     */
    public Map<String, Object> getInputParameters()
    {
        return inputParameters;
    }
}
