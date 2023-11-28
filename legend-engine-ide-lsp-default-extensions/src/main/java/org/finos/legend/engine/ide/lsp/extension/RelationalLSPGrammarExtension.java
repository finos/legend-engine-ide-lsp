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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.grammar.from.RelationalGrammarParserExtension;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Column;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.ColumnMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Database;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Filter;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Join;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Schema;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.View;
import org.finos.legend.pure.m2.relational.M2RelationalPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Extension for the Relational grammar.
 */
public class RelationalLSPGrammarExtension extends AbstractSectionParserLSPGrammarExtension
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RelationalLSPGrammarExtension.class);

    private static final List<String> KEYWORDS = List.of("Database", "Schema", "Table", "View", "include", "Join", "Filter");

    private static final ImmutableList<String> SCHEMA_TRIGGERS = Lists.immutable.with("schema");

    private static final ImmutableList<String> SCHEMA_SUGGESTIONS = Lists.immutable.with(
            "Schema schemaName\n" +
                "(\n" +
                " Table TableName1(column1 INT PRIMARY KEY, column2 DATE)\n" +
                " Table TableName2(column3 VARCHAR(10) PRIMARY KEY)\n" +
                ")\n"
    );

    private static final ImmutableList<String> TABLE_TRIGGERS = Lists.immutable.with("schema");

    private static final ImmutableList<String> TABLE_SUGGESTIONS = Lists.immutable.with(
            "Table TableName1(column1 INT PRIMARY KEY, column2 DATE)\n"
    );

    private static final ImmutableList<String> VIEW_TRIGGERS = Lists.immutable.with("schema");

    private static final ImmutableList<String> VIEW_SUGGESTIONS = Lists.immutable.with(
            "View viewName\n" +
                "(\n" +
                "  field1: table1.column1, \n" +
                "  field2: table2.column2,\n" +
                "  field3: table1.column1 + table2.column2\n" +
                ")\n"
    );

    private static final ImmutableList<String> JOIN_TRIGGERS = Lists.immutable.with("Join ");

    private static final ImmutableList<String> JOIN_SUGGESTIONS = Lists.immutable.with(
            "Join joinName(table1.column1 = table2.column2)"
    );

    private static final ImmutableList<String> FILTER_TRIGGERS = Lists.immutable.with("Filter ");

    private static final ImmutableList<String> FILTER_SUGGESTIONS = Lists.immutable.with(
            "Filter filterName(table1.column1 > 12)"
    );

    private static final ImmutableList<String> BOILERPLATE_SUGGESTIONS = Lists.immutable.with(
            "Database package::path::storeName\n" +
            "(\n" +
            "  Schema schemaName\n" +
            "  (\n" +
            "   Table TableName1(column1 INT PRIMARY KEY, column2 DATE)\n" +
            "   Table TableName2(column3 VARCHAR(10) PRIMARY KEY)\n" +
            "  )\n" +
            ")\n"
    );

    public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        String codeLine = section.getSection().getLine(location.getLine()).substring(0, location.getColumn());
        List<LegendCompletion> legendCompletions = new ArrayList<>();

        if (codeLine.isEmpty())
        {
            return BOILERPLATE_SUGGESTIONS.collect(s -> new LegendCompletion("Relational boilerplate", s));
        }

        if (SCHEMA_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            SCHEMA_SUGGESTIONS.collect(s -> new LegendCompletion("Schema definition", s), legendCompletions);
        }
        if (TABLE_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            TABLE_SUGGESTIONS.collect(s -> new LegendCompletion("Table definition", s), legendCompletions);
        }
        if (VIEW_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            VIEW_SUGGESTIONS.collect(s -> new LegendCompletion("View definition", s), legendCompletions);
        }
        if (JOIN_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            JOIN_SUGGESTIONS.collect(s -> new LegendCompletion("Join definition", s), legendCompletions);
        }
        if (FILTER_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            FILTER_SUGGESTIONS.collect(s -> new LegendCompletion("Filter definition", s), legendCompletions);
        }
        return legendCompletions;
    }

    public RelationalLSPGrammarExtension()
    {
        super(RelationalGrammarParserExtension.NAME, new RelationalGrammarParserExtension());
    }

    @Override
    public String getName()
    {
        return "Relational";
    }

    @Override
    public Iterable<? extends String> getKeywords()
    {
        return KEYWORDS;
    }

    @Override
    protected void forEachChild(PackageableElement element, Consumer<LegendDeclaration> consumer)
    {
        if (element instanceof Database)
        {
            Database db = (Database) element;
            db.schemas.forEach(s -> consumer.accept(getDeclaration(s)));
            db.joins.forEach(j -> consumer.accept(getDeclaration(j)));
            db.filters.forEach(f -> consumer.accept(getDeclaration(f)));
        }
    }

    private LegendDeclaration getDeclaration(Schema schema)
    {
        if (!isValidSourceInfo(schema.sourceInformation))
        {
            LOGGER.warn("Invalid source information for schema {}", schema.name);
            return null;
        }

        LegendDeclaration.Builder builder = LegendDeclaration.builder()
                .withIdentifier(schema.name)
                .withClassifier(M2RelationalPaths.Schema)
                .withLocation(toLocation(schema.sourceInformation));
        schema.tables.forEach(t -> addChildIfNonNull(builder, getDeclaration(t)));
        schema.views.forEach(v -> addChildIfNonNull(builder, getDeclaration(v)));
        return builder.build();
    }

    private LegendDeclaration getDeclaration(Table table)
    {
        if (!isValidSourceInfo(table.sourceInformation))
        {
            LOGGER.warn("Invalid source information for table {}", table.name);
            return null;
        }

        LegendDeclaration.Builder builder = LegendDeclaration.builder()
                .withIdentifier(table.name)
                .withClassifier(M2RelationalPaths.Table)
                .withLocation(toLocation(table.sourceInformation));
        table.columns.forEach(c -> addChildIfNonNull(builder, getDeclaration(c)));
        return builder.build();
    }

    private LegendDeclaration getDeclaration(Column column)
    {
        if (!isValidSourceInfo(column.sourceInformation))
        {
            LOGGER.warn("Invalid source information for column {}", column.name);
            return null;
        }
        return LegendDeclaration.builder()
                .withIdentifier(column.name)
                .withClassifier(M2RelationalPaths.Column)
                .withLocation(toLocation(column.sourceInformation))
                .build();
    }

    private LegendDeclaration getDeclaration(View view)
    {
        if (!isValidSourceInfo(view.sourceInformation))
        {
            LOGGER.warn("Invalid source information for view {}", view.name);
            return null;
        }

        LegendDeclaration.Builder builder = LegendDeclaration.builder()
                .withIdentifier(view.name)
                .withClassifier(M2RelationalPaths.Table)
                .withLocation(toLocation(view.sourceInformation));
        view.columnMappings.forEach(c -> addChildIfNonNull(builder, getDeclaration(c)));
        return builder.build();
    }

    private LegendDeclaration getDeclaration(ColumnMapping columnMapping)
    {
        if (!isValidSourceInfo(columnMapping.sourceInformation))
        {
            LOGGER.warn("Invalid source information for column mapping {}", columnMapping.name);
            return null;
        }
        return LegendDeclaration.builder()
                .withIdentifier(columnMapping.name)
                .withClassifier("meta::relational::mapping::ColumnMapping")
                .withLocation(toLocation(columnMapping.sourceInformation))
                .build();
    }

    private LegendDeclaration getDeclaration(Join join)
    {
        if (!isValidSourceInfo(join.sourceInformation))
        {
            LOGGER.warn("Invalid source information for join {}", join.name);
            return null;
        }
        return LegendDeclaration.builder()
                .withIdentifier(join.name)
                .withClassifier(M2RelationalPaths.Join)
                .withLocation(toLocation(join.sourceInformation))
                .build();
    }

    private LegendDeclaration getDeclaration(Filter filter)
    {
        if (!isValidSourceInfo(filter.sourceInformation))
        {
            LOGGER.warn("Invalid source information for filter {}", filter.name);
            return null;
        }
        return LegendDeclaration.builder()
                .withIdentifier(filter.name)
                .withClassifier(M2RelationalPaths.Filter)
                .withLocation(toLocation(filter.sourceInformation))
                .build();
    }
}
