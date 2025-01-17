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

package org.finos.legend.engine.ide.lsp.extension.dataSpace;

import org.eclipse.collections.api.RichIterable;
import org.finos.legend.engine.ide.lsp.extension.AbstractSectionParserLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendReferenceResolver;
import org.finos.legend.engine.ide.lsp.extension.core.PureLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.language.pure.grammar.from.DataSpaceParserExtension;
import org.finos.legend.engine.protocol.pure.v1.model.context.PackageableElementPointer;
import org.finos.legend.engine.protocol.pure.v1.model.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.dataSpace.DataSpace;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.dataSpace.DataSpaceElementPointer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.dataSpace.DataSpaceExecutable;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.dataSpace.DataSpaceExecutionContext;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.dataSpace.DataSpacePackageableElementExecutable;
import org.finos.legend.pure.generated.Root_meta_pure_metamodel_dataSpace_DataSpace;
import org.finos.legend.pure.generated.Root_meta_pure_metamodel_dataSpace_DataSpaceExecutable;
import org.finos.legend.pure.generated.Root_meta_pure_metamodel_dataSpace_DataSpaceTemplateExecutable;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Extension for the DataSpace grammar.
 */
public class DataSpaceLSPGrammarExtension extends AbstractSectionParserLSPGrammarExtension
  {
    public DataSpaceLSPGrammarExtension()
    {
        super(DataSpaceParserExtension.NAME, new DataSpaceParserExtension());
    }

    @Override
    protected Stream<Optional<LegendReferenceResolver>> getReferenceResolvers(SectionState section, PackageableElement packageableElement, Optional<CoreInstance> coreInstance)
    {
        DataSpace dataSpace = (DataSpace) packageableElement;
        Stream<Optional<LegendReferenceResolver>> stereoTypeReferences = PureLSPGrammarExtension.toStereotypeReferences(dataSpace.stereotypes);
        Stream<Optional<LegendReferenceResolver>> taggedValueReferences = PureLSPGrammarExtension.toTaggedValueReferences(dataSpace.taggedValues);
        Stream<Optional<LegendReferenceResolver>> executionContextReferences = toExecutionContextReferences(dataSpace.executionContexts);
        Stream<Optional<LegendReferenceResolver>> elementReferences = toElementReferences(dataSpace.elements);
        Stream<Optional<LegendReferenceResolver>> executableReferences = toExecutableReferences(dataSpace.executables);
        Stream<Optional<LegendReferenceResolver>> coreReferences = Stream.empty();
        if (coreInstance.isPresent())
        {
            coreReferences = toReferences((Root_meta_pure_metamodel_dataSpace_DataSpace) coreInstance.get());
        }
        return Stream.of(stereoTypeReferences, taggedValueReferences, executionContextReferences, elementReferences, executableReferences, coreReferences)
                .flatMap(java.util.function.Function.identity());
    }

    private Stream<Optional<LegendReferenceResolver>> toExecutionContextReferences(List<DataSpaceExecutionContext> executionContexts)
    {
        if (executionContexts == null)
        {
            return Stream.empty();
        }
        return executionContexts.stream()
                .flatMap(executionContext ->
                {
                    Optional<LegendReferenceResolver> mappingReference = toReference(executionContext.mapping);
                    Optional<LegendReferenceResolver> runtimeReference = toReference(executionContext.defaultRuntime);
                    return Stream.of(mappingReference, runtimeReference);
                });
    }

    private Stream<Optional<LegendReferenceResolver>> toElementReferences(List<DataSpaceElementPointer> elements)
    {
        if (elements == null)
        {
            return Stream.empty();
        }
        return elements.stream()
                .map(this::toReference);
    }

    private Stream<Optional<LegendReferenceResolver>> toExecutableReferences(List<DataSpaceExecutable> executables)
    {
        if (executables == null)
        {
            return Stream.empty();
        }
        return executables.stream()
                .map(this::toReference);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(Root_meta_pure_metamodel_dataSpace_DataSpace dataSpace)
    {
        RichIterable<? extends Root_meta_pure_metamodel_dataSpace_DataSpaceExecutable> dataSpaceExecutables = dataSpace._executables();
        if (dataSpaceExecutables == null)
        {
            return Stream.empty();
        }
        return StreamSupport.stream(dataSpaceExecutables.spliterator(), false)
                .flatMap(dataSpaceExecutable ->
                {
                    if (!(dataSpaceExecutable instanceof Root_meta_pure_metamodel_dataSpace_DataSpaceTemplateExecutable))
                    {
                        return Stream.of(Optional.empty());
                    }
                    return FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(((Root_meta_pure_metamodel_dataSpace_DataSpaceTemplateExecutable) dataSpaceExecutable)._query()));
                });
    }

    private Optional<LegendReferenceResolver> toReference(DataSpaceElementPointer dataSpaceElementPointer)
    {
        return LegendReferenceResolver.newReferenceResolver(
                dataSpaceElementPointer.sourceInformation,
                x -> x.resolvePackageableElement(dataSpaceElementPointer.path, dataSpaceElementPointer.sourceInformation)
        );
    }

    private Optional<LegendReferenceResolver> toReference(PackageableElementPointer packageableElementPointer)
    {
        return LegendReferenceResolver.newReferenceResolver(
                packageableElementPointer.sourceInformation,
                x -> x.resolvePackageableElement(packageableElementPointer.path, packageableElementPointer.sourceInformation)
        );
    }

    private Optional<LegendReferenceResolver> toReference(DataSpaceExecutable dataSpaceExecutable)
    {
        if (!(dataSpaceExecutable instanceof DataSpacePackageableElementExecutable))
        {
            return Optional.empty();
        }
        return toReference(((DataSpacePackageableElementExecutable) dataSpaceExecutable).executable);
    }
}
