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
import org.finos.legend.engine.ide.lsp.extension.AbstractSectionParserLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendReferenceResolver;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtension;
import org.finos.legend.engine.protocol.functionActivator.metamodel.FunctionActivator;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Function;
import org.finos.legend.pure.generated.Root_meta_external_function_activator_FunctionActivator;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

public abstract class FunctionActivatorLSPGrammarExtension extends AbstractSectionParserLSPGrammarExtension
{
    protected FunctionActivatorLSPGrammarExtension(String parserName, PureGrammarParserExtension extension)
    {
        super(parserName, extension);
    }

    public abstract String getSnippet(Function function, List<PackageableElement> elements);

    @Override
    protected Stream<Optional<LegendReferenceResolver>> getReferenceResolvers(SectionState section, PackageableElement packageableElement, Optional<CoreInstance> coreInstance)
    {
        FunctionActivator functionActivator = (FunctionActivator) packageableElement;

        Stream<Optional<LegendReferenceResolver>> stereotypes = functionActivator.stereotypes.stream().flatMap(FunctionActivatorLSPGrammarExtension::toReferences);
        Stream<Optional<LegendReferenceResolver>> tags = functionActivator.taggedValues.stream().flatMap(FunctionActivatorLSPGrammarExtension::toReferences);

        Optional<LegendReferenceResolver> functionPointer = coreInstance.map(Root_meta_external_function_activator_FunctionActivator.class::cast)
                .flatMap(x -> LegendReferenceResolver.newReferenceResolver(functionActivator.function.sourceInformation, x._function()));

        return Stream.concat(Stream.of(functionPointer), Stream.concat(stereotypes, tags));
    }
}
