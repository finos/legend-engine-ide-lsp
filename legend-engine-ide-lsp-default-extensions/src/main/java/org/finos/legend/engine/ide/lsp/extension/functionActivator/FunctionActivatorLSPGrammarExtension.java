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

import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.engine.ide.lsp.extension.AbstractSectionParserLSPGrammarExtension;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.HelperModelBuilder;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtension;
import org.finos.legend.engine.language.pure.grammar.to.HelperDomainGrammarComposer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Function;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Multiplicity;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.Variable;

import java.util.List;
import java.util.Objects;

public abstract class FunctionActivatorLSPGrammarExtension extends AbstractSectionParserLSPGrammarExtension
{
    protected FunctionActivatorLSPGrammarExtension(String parserName, PureGrammarParserExtension extension)
    {
        super(parserName, extension);
    }

    public abstract String getSnippet(Function function, List<PackageableElement> elements);

    // TODO: Remove once getFunctionDescriptor(Function function) API gets created in Legend Engine
    protected String getFunctionDescriptor(Function function)
    {
        StringBuilder builder = new StringBuilder();
        String packageName = function._package;
        String functionName = HelperModelBuilder.getFunctionNameWithoutSignature(function);
        String functionSignature = LazyIterate.collect(function.parameters, this::getParameterSignature).select(Objects::nonNull).makeString(",");
        String returnValueSignature = getParameterSignature(function.returnType, function.returnMultiplicity);
        builder.append(packageName)
                .append("::")
                .append(functionName)
                .append("(")
                .append(functionSignature)
                .append("):")
                .append(returnValueSignature);
        return builder.toString();
    }

    // TODO: Remove once getFunctionDescriptor(Function function) API gets created in Legend Engine
    private String getParameterSignature(Variable p)
    {
        return getParameterSignature(p._class, p.multiplicity);
    }

    // TODO: Remove once getFunctionDescriptor(Function function) API gets created in Legend Engine
    private String getParameterSignature(String _class, Multiplicity multiplicity)
    {
        return _class != null ? getClassSignature(_class) + "[" + HelperDomainGrammarComposer.renderMultiplicity(multiplicity) + "]" : null;
    }

    // TODO: Remove once getFunctionDescriptor(Function function) API gets created in Legend Engine
    private String getClassSignature(String _class)
    {
        if (_class == null)
        {
            return null;
        }
        else
        {
            return _class.contains("::") ? _class.substring(_class.lastIndexOf("::") + 2) : _class;
        }
    }
}
