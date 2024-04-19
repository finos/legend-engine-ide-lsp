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

package org.finos.legend.engine.ide.lsp.extension.functionActivator;

import java.util.List;
import org.finos.legend.engine.language.hostedService.grammar.from.HostedServiceGrammarParserExtension;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.HelperModelBuilder;
import org.finos.legend.engine.language.pure.grammar.to.HelperValueSpecificationGrammarComposer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Function;

public class HostedServiceLSPGrammarExtension extends FunctionActivatorLSPGrammarExtension
{
    public HostedServiceLSPGrammarExtension()
    {
        super(HostedServiceGrammarParserExtension.NAME, new HostedServiceGrammarParserExtension());
    }

    @Override
    public String getSnippet(Function function, List<PackageableElement> elements)
    {
        StringBuilder builder = new StringBuilder();
        String functionName = HelperModelBuilder.getFunctionNameWithoutSignature(function);
        String packageName = function._package;
        builder.append("\n\n###HostedService\n")
                .append(String.format("HostedService ${1:%s}::${2:%sHostedServiceActivator}\n", packageName, functionName))
                .append("{\n")
                .append("\tpattern: '/${3:Please provide a pattern}';\n")
                .append("\townership: Deployment { identifier: '${4:DID}' };\n")
                .append(String.format("\tfunction: %s;\n", HelperValueSpecificationGrammarComposer.getFunctionDescriptor(function)))
                .append("\tdocumentation: '${5:Please provide a documentation}';\n")
                .append("\tautoActivateUpdates: ${6|true,false|};\n")
                .append("}");
        return builder.toString();
    }

    @Override
    public String getName()
    {
        return "HostedService";
    }
}
