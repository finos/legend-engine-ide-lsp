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

package org.finos.legend.engine.ide.lsp.extension.core;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.ide.lsp.extension.LegendReferenceResolver;
import org.finos.legend.pure.m3.coreinstance.meta.pure.graphFetch.GraphFetchTree;
import org.finos.legend.pure.m3.coreinstance.meta.pure.graphFetch.PropertyGraphFetchTree;
import org.finos.legend.pure.m3.coreinstance.meta.pure.graphFetch.RootGraphFetchTree;
import org.finos.legend.pure.m3.coreinstance.meta.pure.graphFetch.SubTypeGraphFetchTree;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.constraint.Constraint;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.ConcreteFunctionDefinition;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.Function;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.FunctionDefinition;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.path.Path;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.path.PropertyPathElement;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.FunctionType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.generics.GenericType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.FunctionExpression;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.InstanceValue;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.VariableExpression;
import org.finos.legend.pure.m3.coreinstance.meta.pure.store.RelationStoreAccessor;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class FunctionExpressionNavigator implements DefaultFunctionExpressionNavigator
{
    private static final SourceInformation UNKNOWN_SOURCE_INFORMATION = new SourceInformation("X", 0, 0, 0, 0);

    @Override
    public Stream<Optional<LegendReferenceResolver>> findReferences(Optional<CoreInstance> coreInstance)
    {
        return findReferences(coreInstance, List.of());
    }

    private Stream<Optional<LegendReferenceResolver>> findReferences(Optional<CoreInstance> optionalCoreInstance, List<VariableExpression> variableExpressions)
    {
        if (optionalCoreInstance.isEmpty())
        {
            return Stream.empty();
        }

        CoreInstance coreInstance = optionalCoreInstance.get();
        if (coreInstance instanceof FunctionDefinition)
        {
            FunctionDefinition<?> functionDefinition = (FunctionDefinition<?>) coreInstance;
            FunctionType functionType = (FunctionType) functionDefinition._classifierGenericType()._typeArguments().getOnly()._rawType();
            MutableList<VariableExpression> allVariableExpressions = Lists.mutable.withAll(variableExpressions).withAll(functionType._parameters()).asUnmodifiable();
            Stream<Optional<LegendReferenceResolver>> expressionSequenceReferences = findReferences(functionDefinition._expressionSequence(), allVariableExpressions);

            Stream<Optional<LegendReferenceResolver>> preConstraintReferences = Stream.empty();
            Stream<Optional<LegendReferenceResolver>> postConstraintReferences = Stream.empty();
            if (functionDefinition instanceof ConcreteFunctionDefinition<?>)
            {
                ConcreteFunctionDefinition<?> concreteFunctionDefinition = (ConcreteFunctionDefinition<?>) functionDefinition;
                preConstraintReferences = findReferences(concreteFunctionDefinition._preConstraints(), allVariableExpressions);
                postConstraintReferences = findReferences(concreteFunctionDefinition._postConstraints(), allVariableExpressions);
            }
            // TODO: Revisit return type
            GenericType returnType = functionType._returnType();
            Stream<Optional<LegendReferenceResolver>> returnTypeReferences = getLegendReference(returnType.getSourceInformation(), returnType._rawType());
            return Stream.of(expressionSequenceReferences, returnTypeReferences, preConstraintReferences, postConstraintReferences).flatMap(java.util.function.Function.identity());
        }

        else if (coreInstance instanceof FunctionExpression)
        {
            FunctionExpression functionExpression = (FunctionExpression) coreInstance;
            Stream<Optional<LegendReferenceResolver>> paramReferences = findReferences(functionExpression._parametersValues(), variableExpressions);
            Function<?> function = functionExpression._func();
            Stream<Optional<LegendReferenceResolver>> reference = getLegendReference(functionExpression.getSourceInformation(), function);
            return Stream.concat(paramReferences, reference);
        }

        else if (coreInstance instanceof InstanceValue)
        {
            InstanceValue instanceValue = (InstanceValue) coreInstance;
            RichIterable<? extends CoreInstance> values = instanceValue._values().selectInstancesOf(CoreInstance.class);
            Stream<Optional<LegendReferenceResolver>> valueReferences = Stream.empty();
            if (values.size() == 1)
            {
                CoreInstance value = values.getOnly();
                if (value instanceof PackageableElement)
                {
                    PackageableElement packageableElement = (PackageableElement) value;
                    valueReferences = getLegendReference(instanceValue.getSourceInformation(), packageableElement);
                }
            }
            return Stream.concat(valueReferences, findReferences(values, variableExpressions));
        }

        else if (coreInstance instanceof RelationStoreAccessor)
        {
            RelationStoreAccessor relationStoreAccessor = (RelationStoreAccessor) coreInstance;
            return getLegendReference(relationStoreAccessor.getSourceInformation(), (CoreInstance) relationStoreAccessor._sourceElement());
        }

        else if (coreInstance instanceof VariableExpression)
        {
            VariableExpression variableExpression = (VariableExpression) coreInstance;
            if (variableExpression._name() != null)
            {
                List<VariableExpression> matchedVariableExpressions = variableExpressions.stream()
                        .filter(ve -> variableExpression._name().equals(ve._name()))
                        .collect(Collectors.toList());
                if (!matchedVariableExpressions.isEmpty())
                {
                    VariableExpression matchedVariableExpression = matchedVariableExpressions.get(matchedVariableExpressions.size() - 1);
                    return getLegendReference(variableExpression.getSourceInformation(), matchedVariableExpression);
                }
            }
            return Stream.empty();
        }

        else if (coreInstance instanceof GraphFetchTree)
        {
            GraphFetchTree graphFetchTree = (GraphFetchTree) coreInstance;
            Stream<Optional<LegendReferenceResolver>> classReference = Stream.empty();
            if (graphFetchTree instanceof RootGraphFetchTree)
            {
                RootGraphFetchTree rootGraphFetchTree = (RootGraphFetchTree) graphFetchTree;
                classReference = getLegendReference(rootGraphFetchTree.getSourceInformation(), rootGraphFetchTree._class());
            }

            Stream<Optional<LegendReferenceResolver>> propertyReference = Stream.empty();
            if (graphFetchTree instanceof PropertyGraphFetchTree)
            {
                PropertyGraphFetchTree propertyGraphFetchTree = (PropertyGraphFetchTree) graphFetchTree;
                propertyReference = getLegendReference(propertyGraphFetchTree.getSourceInformation(), propertyGraphFetchTree._property());
            }

            Stream<Optional<LegendReferenceResolver>> subTypeReference = Stream.empty();
            if (graphFetchTree instanceof SubTypeGraphFetchTree)
            {
                SubTypeGraphFetchTree subTypeGraphFetchTree = (SubTypeGraphFetchTree) graphFetchTree;
                subTypeReference = getLegendReference(subTypeGraphFetchTree.getSourceInformation(), subTypeGraphFetchTree._subTypeClass());
            }

            Stream<Optional<LegendReferenceResolver>> subTreeReferences = findReferences(graphFetchTree._subTrees(), variableExpressions);
            Stream<Optional<LegendReferenceResolver>> subTypeTreeReferences = findReferences(graphFetchTree._subTypeTrees(), variableExpressions);
            return Stream.of(classReference, propertyReference, subTypeReference, subTreeReferences, subTypeTreeReferences)
                    .flatMap(java.util.function.Function.identity());
        }

        else if (coreInstance instanceof Path)
        {
            Path path = (Path) coreInstance;
            return findReferences(path._path(), variableExpressions);
        }

        else if (coreInstance instanceof PropertyPathElement)
        {
            PropertyPathElement propertyPathElement = (PropertyPathElement) coreInstance;
            return findReferences(propertyPathElement._parameters(), variableExpressions);
        }

        else if (coreInstance instanceof Constraint)
        {
            Constraint constraint = (Constraint) coreInstance;
            Stream<Optional<LegendReferenceResolver>> functionDefinitionReferences = findReferences(Optional.ofNullable(constraint._functionDefinition()), variableExpressions);
            Stream<Optional<LegendReferenceResolver>> messageFunctionReferences = findReferences(Optional.ofNullable(constraint._messageFunction()), variableExpressions);
            return Stream.concat(functionDefinitionReferences, messageFunctionReferences);
        }

        return Stream.empty();
    }

    private Stream<Optional<LegendReferenceResolver>> findReferences(RichIterable<? extends CoreInstance> coreInstances, List<VariableExpression> variableExpressions)
    {
        return StreamSupport.stream(coreInstances.spliterator(), false)
                .flatMap(c -> findReferences(Optional.ofNullable(c), variableExpressions));
    }

    private Stream<Optional<LegendReferenceResolver>> getLegendReference(SourceInformation sourceInformation, CoreInstance coreInstance)
    {
        if (isValidSourceInformation(sourceInformation) && coreInstance != null && isValidSourceInformation(coreInstance.getSourceInformation()) && !sourceInformation.equals(coreInstance.getSourceInformation()))
        {
            return Stream.of(LegendReferenceResolver.newReferenceResolver(sourceInformation, coreInstance));
        }
        return Stream.empty();
    }

    private boolean isValidSourceInformation(SourceInformation sourceInformation)
    {
        return sourceInformation != null && !sourceInformation.equals(UNKNOWN_SOURCE_INFORMATION);
    }
}
