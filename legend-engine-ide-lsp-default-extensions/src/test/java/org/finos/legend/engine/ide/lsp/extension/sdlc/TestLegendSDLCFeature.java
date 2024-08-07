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

package org.finos.legend.engine.ide.lsp.extension.sdlc;

import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestLegendSDLCFeature
{
    @Test
    void testEntityJsonToPureText() throws IOException
    {
        LegendSDLCFeatureImpl legendSDLCFeature = new LegendSDLCFeatureImpl();
        String pureText = legendSDLCFeature.entityJsonToPureText("{\n" +
                "  \"content\": {\n" +
                "    \"_type\": \"class\",\n" +
                "    \"name\": \"A\",\n" +
                "    \"package\": \"model\",\n" +
                "    \"properties\": [\n" +
                "      {\n" +
                "        \"multiplicity\": {\n" +
                "          \"lowerBound\": 1,\n" +
                "          \"upperBound\": 1\n" +
                "        },\n" +
                "        \"name\": \"name\",\n" +
                "        \"type\": \"String\"\n" +
                "      }\n" +
                "    ]\n" +
                "  },\n" +
                "  \"classifierPath\": \"meta::pure::metamodel::type::Class\"\n" +
                "}\n");

        Assertions.assertEquals(
                "Class model::A\n" +
                "{\n" +
                "  name: String[1];\n" +
                "}\n", pureText);
    }
}
