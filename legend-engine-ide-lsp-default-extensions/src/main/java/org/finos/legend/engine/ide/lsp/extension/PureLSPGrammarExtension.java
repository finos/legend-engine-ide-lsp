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

import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.language.pure.grammar.from.domain.DomainParser;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Association;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Enumeration;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Function;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Measure;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Multiplicity;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Profile;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Property;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.QualifiedProperty;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Extension for the Pure grammar.
 */
public class PureLSPGrammarExtension extends AbstractLegacyParserLSPGrammarExtension
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PureLSPGrammarExtension.class);

    private static final List<String> KEYWORDS = List.copyOf(PrimitiveUtilities.getPrimitiveTypeNames().toSet()
            .with("Association")
            .with("Class")
            .with("Enum")
            .with("function")
            .with("import")
            .with("let")
            .with("native function")
            .with("Profile")
    );

    public PureLSPGrammarExtension()
    {
        super(new DomainParser());
    }

    @Override
    public Iterable<? extends String> getKeywords()
    {
        return KEYWORDS;
    }

    @Override
    protected String getClassifier(PackageableElement element)
    {
        if (element instanceof Class)
        {
            return M3Paths.Class;
        }
        if (element instanceof Enumeration)
        {
            return M3Paths.Enumeration;
        }
        if (element instanceof Association)
        {
            return M3Paths.Association;
        }
        if (element instanceof Profile)
        {
            return M3Paths.Profile;
        }
        if (element instanceof Function)
        {
            return M3Paths.Function;
        }
        if (element instanceof Measure)
        {
            return M3Paths.Measure;
        }
        LOGGER.warn("Unhandled element type: {}", element.getClass());
        return null;
    }

    @Override
    protected void forEachChild(PackageableElement element, Consumer<LegendDeclaration> consumer)
    {
        if (element instanceof Class)
        {
            Class _class = (Class) element;
            _class.properties.forEach(p -> consumer.accept(getDeclaration(p)));
            _class.qualifiedProperties.forEach(qp -> consumer.accept(getDeclaration(qp)));
        }
        else if (element instanceof Enumeration)
        {
            Enumeration _enum = (Enumeration) element;
            String path = _enum.getPath();
            _enum.values.forEach(value ->
            {
                if (isValidSourceInfo(value.sourceInformation))
                {
                    consumer.accept(LegendDeclaration.builder()
                            .withIdentifier(value.value)
                            .withClassifier(path)
                            .withLocation(toLocation(value.sourceInformation))
                            .build());
                }
            });
        }
        else if (element instanceof Association)
        {
            Association association = (Association) element;
            association.properties.forEach(p -> consumer.accept(getDeclaration(p)));
            association.qualifiedProperties.forEach(qp -> consumer.accept(getDeclaration(qp)));
        }
    }

    private LegendDeclaration getDeclaration(Property property)
    {
        if (!isValidSourceInfo(property.sourceInformation))
        {
            LOGGER.warn("Invalid source information for property {}", property.name);
            return null;
        }

        return LegendDeclaration.builder()
                .withIdentifier(property.name)
                .withClassifier(M3Paths.Property)
                .withLocation(toLocation(property.sourceInformation))
                .build();
    }

    private LegendDeclaration getDeclaration(QualifiedProperty property)
    {
        if (!isValidSourceInfo(property.sourceInformation))
        {
            LOGGER.warn("Invalid source information for qualified property {}", property.name);
            return null;
        }

        StringBuilder builder = new StringBuilder(property.name).append('(');
        int len = builder.length();
        property.parameters.forEach(p ->
        {
            if (builder.length() > len)
            {
                builder.append(',');
            }
            builder.append(p._class).append(":[");
            Multiplicity mult = p.multiplicity;
            int lower = mult.lowerBound;
            Integer upper = mult.getUpperBound();
            if ((upper == null) ? (lower != 0) : (lower != upper))
            {
                builder.append(lower).append("..");
            }
            if (upper == null)
            {
                builder.append('*');
            }
            else
            {
                builder.append(upper.intValue());
            }
            builder.append(']');
        });
        builder.append(')');
        return LegendDeclaration.builder()
                .withIdentifier(builder.toString())
                .withClassifier(M3Paths.QualifiedProperty)
                .withLocation(toLocation(property.sourceInformation))
                .build();
    }
}
