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
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.engine.ide.lsp.extension.LegendReferenceResolver;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.HelperModelBuilder;
import org.finos.legend.engine.language.pure.grammar.to.HelperValueSpecificationGrammarComposer;
import org.finos.legend.engine.language.snowflakeApp.grammar.from.SnowflakeAppGrammarParserExtension;
import org.finos.legend.engine.protocol.pure.m3.PackageableElement;
import org.finos.legend.engine.protocol.pure.m3.function.Function;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.PackageableConnection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.connection.DatabaseType;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.connection.RelationalDatabaseConnection;
import org.finos.legend.engine.protocol.snowflakeApp.metamodel.SnowflakeApp;
import org.finos.legend.engine.protocol.snowflakeApp.metamodel.SnowflakeAppDeploymentConfiguration;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

public class SnowflakeLSPGrammarExtension extends FunctionActivatorLSPGrammarExtension
{
    public SnowflakeLSPGrammarExtension()
    {
        super(SnowflakeAppGrammarParserExtension.NAME, new SnowflakeAppGrammarParserExtension());
    }

    @Override
    public String getSnippet(Function function, List<PackageableElement> elements)
    {
        StringBuilder builder = new StringBuilder();
        String functionName = HelperModelBuilder.getFunctionNameWithoutSignature(function);
        String packageName = function._package;
        builder.append("\n\n###Snowflake\n")
                .append(String.format("SnowflakeApp ${1:%s}::${2:%sSnowflakeActivator}\n", packageName, functionName))
                .append("{\n")
                .append(String.format("\tapplicationName: '${3:%sSnowflakeActivator}';\n", functionName))
                .append(String.format("\tfunction: %s;\n", HelperValueSpecificationGrammarComposer.getFunctionDescriptor(function)))
                .append("\townership: Deployment { identifier: '${4:DID}' };\n")
                .append("\tdescription: '${5:Please provide a description}';\n")
                .append(String.format("\tactivationConfiguration: %s;\n", buildConnectionSuggestions(elements)))
                .append("}");
        return builder.toString();
    }

    private String buildConnectionSuggestions(List<PackageableElement> elements)
    {
        String connectionSuggestions = LazyIterate.selectInstancesOf(elements, PackageableConnection.class)
                .collectIf(connection -> connection.connectionValue instanceof RelationalDatabaseConnection && ((RelationalDatabaseConnection) connection.connectionValue).databaseType == DatabaseType.Snowflake, PackageableElement::getPath)
                .makeString(",");
        if (connectionSuggestions.isEmpty())
        {
            return "$6";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("${6|")
                .append(connectionSuggestions)
                .append("|}");
        return builder.toString();
    }

    @Override
    public String getName()
    {
        return "Snowflake";
    }

    @Override
    protected Stream<Optional<LegendReferenceResolver>> getReferenceResolvers(SectionState section, PackageableElement packageableElement, Optional<CoreInstance> coreInstance)
    {
        SnowflakeApp functionActivator = (SnowflakeApp) packageableElement;
        SnowflakeAppDeploymentConfiguration configuration = (SnowflakeAppDeploymentConfiguration) functionActivator.activationConfiguration;
        Optional<LegendReferenceResolver> connectionResolver = Optional.ofNullable(configuration).flatMap(config ->
                LegendReferenceResolver.newReferenceResolver(config.activationConnection.sourceInformation,
                        x -> x.resolveConnection(config.activationConnection.connection, config.activationConnection.sourceInformation))
        );
        return Stream.concat(super.getReferenceResolvers(section, packageableElement, coreInstance), Stream.of(connectionResolver));
    }
}
