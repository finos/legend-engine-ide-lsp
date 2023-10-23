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
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParser;
import org.finos.legend.engine.language.pure.grammar.from.domain.DomainParser;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElementVisitor;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.PackageableConnection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.data.DataElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Association;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Enumeration;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Function;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Measure;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Multiplicity;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Profile;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Property;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.QualifiedProperty;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.PackageableRuntime;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.section.SectionIndex;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Extension for the Pure grammar.
 */
class PureLSPGrammarExtension implements LegendLSPGrammarExtension
{
    private static final List<String> KEYWORDS = List.of("Class", "Profile", "Enum", "Association", "function", "native function",
            "Boolean", "Integer", "Float", "Decimal", "Number", "String", "Date", "DateTime", "StrictDate", "StrictTime",
            "import", "let");

    @Override
    public String getName()
    {
        return "Pure";
    }

    @Override
    public Iterable<? extends String> getKeywords()
    {
        return KEYWORDS;
    }

    @Override
    protected String getClassifier(PackageableElement element)
    {
        return element.accept(new PackageableElementVisitor<>()
        {
            @Override
            public String visit(PackageableElement element)
            {
                return null;
            }

            @Override
            public String visit(Profile profile)
            {
                return M3Paths.Profile;
            }

            @Override
            public String visit(Enumeration _enum)
            {
                return M3Paths.Enumeration;
            }

            @Override
            public String visit(Class _class)
            {
                return M3Paths.Class;
            }

            @Override
            public String visit(Association association)
            {
                return M3Paths.Association;
            }

            @Override
            public String visit(Function function)
            {
                return M3Paths.Function;
            }

            @Override
            public String visit(Measure measure)
            {
                return M3Paths.Measure;
            }

            @Override
            public String visit(SectionIndex sectionIndex)
            {
                return null;
            }

            @Override
            public String visit(Mapping mapping)
            {
                return null;
            }

            @Override
            public String visit(PackageableRuntime packageableRuntime)
            {
                return null;
            }

            @Override
            public String visit(PackageableConnection packageableConnection)
            {
                return null;
            }

            @Override
            public String visit(DataElement dataElement)
            {
                return null;
            }
        });
    }

    @Override
    protected void forEachChild(PackageableElement element, Consumer<LegendDeclaration> consumer)
    {
        super.forEachChild(element, consumer);
        element.accept(new PackageableElementVisitor<Void>()
        {
            @Override
            public Void visit(PackageableElement element)
            {
                return null;
            }

            @Override
            public Void visit(Profile profile)
            {
                return null;
            }

            @Override
            public Void visit(Enumeration _enum)
            {
                String path = _enum.getPath();
                _enum.values.forEach(value ->
                {
                    TextInterval location = toLocation(value.sourceInformation);
                    if (location != null)
                    {
                        consumer.accept(LegendDeclaration.builder()
                                .withIdentifier(value.value)
                                .withClassifier(path)
                                .withLocation(location)
                                .build());
                    }
                });
                return null;
            }

            @Override
            public Void visit(Class _class)
            {
                _class.properties.forEach(p -> consumer.accept(getDeclaration(p)));
                _class.qualifiedProperties.forEach(qp -> consumer.accept(getDeclaration(qp)));
                return null;
            }

            @Override
            public Void visit(Association association)
            {
                association.properties.forEach(p -> consumer.accept(getDeclaration(p)));
                association.qualifiedProperties.forEach(qp -> consumer.accept(getDeclaration(qp)));
                return null;
            }

            @Override
            public Void visit(Function function)
            {
                return null;
            }

            @Override
            public Void visit(Measure measure)
            {
                return null;
            }

            @Override
            public Void visit(SectionIndex sectionIndex)
            {
                return null;
            }

            @Override
            public Void visit(Mapping mapping)
            {
                return null;
            }

            @Override
            public Void visit(PackageableRuntime packageableRuntime)
            {
                return null;
            }

            @Override
            public Void visit(PackageableConnection packageableConnection)
            {
                return null;
            }

            @Override
            public Void visit(DataElement dataElement)
            {
                return null;
            }
        });
    }

    private LegendDeclaration getDeclaration(Property property)
    {
        TextInterval location = toLocation(property.sourceInformation);
        if (location == null)
        {
            return null;
        }

        return LegendDeclaration.builder()
                .withIdentifier(property.name)
                .withClassifier(M3Paths.Property)
                .withLocation(location)
                .build();
    }

    private LegendDeclaration getDeclaration(QualifiedProperty property)
    {
        TextInterval location = toLocation(property.sourceInformation);
        if (location == null)
        {
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
                .withLocation(location)
                .build();
    }

    @Override
    public String getParsingError(String code) //GrammarSection section
    {
        String parsingErrorMessage = null;
        try
        {
            PureGrammarParser parser = PureGrammarParser.newInstance();
            parser.parseModel(code);
        }
        catch (Exception e)
        {
            parsingErrorMessage = e.getMessage();
        }

        return parsingErrorMessage;
    }
}
