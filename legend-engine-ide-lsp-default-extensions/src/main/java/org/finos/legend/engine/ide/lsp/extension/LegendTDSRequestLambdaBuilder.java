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

package org.finos.legend.engine.ide.lsp.extension;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.ide.lsp.extension.agGrid.ColumnType;
import org.finos.legend.engine.ide.lsp.extension.agGrid.Filter;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSGroupBy;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSSort;
import org.finos.legend.engine.ide.lsp.extension.agGrid.FilterOperation;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Function;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Multiplicity;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.ValueSpecification;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.Variable;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.application.AppliedFunction;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.application.AppliedProperty;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.PrimitiveValueSpecification;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.CString;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.CBoolean;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.CDateTime;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.CDecimal;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.CStrictDate;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.CInteger;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.CFloat;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.Lambda;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.Collection;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class LegendTDSRequestLambdaBuilder 
{
    private static final String FILTER_FUNCTION_NAME = "filter";
    private static final String SORT_FUNCTION_NAME = "sort";
    private static final String GROUPBY_FUNCTION_NAME = "groupBy";
    private static final String AND_FILTER_OPERATION_NAME = "and";

    private static final String DEFAULT_VARIABLE_NAME = "x";

    private static String getTDSRowFunction(ColumnType columnType)
    {
        switch (columnType)
        {
            case String:
                return "getString";
            case Boolean:
                return "getBoolean";
            case Number:
                return "getNumber";
            case Integer:
                return "getInteger";
            case Float:
                return "getFloat";
            case Decimal:
                return "getDecimal";
            case Date:
                return "getDate";
            case DateTime:
                return "getDateTime";
            case StrictDate:
                return "getStrictDate";
            default:
                throw new RuntimeException("Unsupported tds column type " + columnType);
        }
    }

    private static PrimitiveValueSpecification getPrimitiveValueSpecification(ColumnType type, Object column)
    {
        switch (type)
        {
            case String:
                return new CString((String) column);
            case Boolean:
                return new CBoolean((Boolean) column);
            case Number:
            case Decimal:
                return new CDecimal((BigDecimal) column);
            case Integer:
                return new CInteger((Long) column);
            case Float:
                return new CFloat((Double) column);
            case Date:
            case DateTime:
                return new CDateTime((String) column);
            case StrictDate:
                return new CStrictDate((String) column);
            default:
                throw new RuntimeException("Unsupported tds column type " + type);
        }
    }

    public static void updateParentFunction(Function parent, String functionName, List<ValueSpecification> child)
    {
        parent.body.addAll(child);
        AppliedFunction childFunc = new AppliedFunction();
        childFunc.function = functionName;
        childFunc.parameters = parent.body;
        parent.body = Lists.mutable.of(childFunc);
    }

    public static void processFilterOperations(Function function, List<Filter> filters)
    {
        if (filters.size() == 0)
        {
            return;
        }
        Lambda filterLambda = new Lambda();
        filterLambda.body = Lists.mutable.empty();
        filterLambda.multiplicity = filters.size() == 1 ? Multiplicity.PURE_ONE : Multiplicity.PURE_MANY;
        filters.forEach(filterValue ->
        {
            if (filterValue.getOperation().equals(FilterOperation.NOT_EQUAL) || filterValue.getOperation().equals(FilterOperation.NOT_BLANK))
            {
                throw new RuntimeException("Unsupported filter operation " + filterValue.getOperation());
            }
            AppliedFunction filterCondition = new AppliedFunction();
            filterCondition.parameters = Lists.mutable.empty();
            filterCondition.function = filterValue.getOperation().getValue();
            filterCondition.multiplicity = Multiplicity.PURE_ONE;

            AppliedProperty property = new AppliedProperty();
            property.property = getTDSRowFunction(filterValue.getColumnType());
            property._class = filterValue.getColumnType().toString();
            Variable x = new Variable();
            x.name = DEFAULT_VARIABLE_NAME;
            property.parameters = Lists.mutable.of(x, new CString(filterValue.getColumn()));

            filterCondition.parameters.add(property);
            filterCondition.parameters.add(getPrimitiveValueSpecification(filterValue.getColumnType(), filterValue.getValue()));

            filterLambda.body.add(filterCondition);
            if (filterLambda.body.size() > 1)
            {
                AppliedFunction andFunc = new AppliedFunction();
                andFunc.function = AND_FILTER_OPERATION_NAME;
                andFunc.multiplicity = Multiplicity.PURE_ONE;
                andFunc.parameters = filterLambda.body;
                filterLambda.body = Lists.mutable.of(andFunc);
            }
            filterLambda.parameters = Lists.mutable.of(x);
        });
        updateParentFunction(function, FILTER_FUNCTION_NAME, Lists.mutable.of(filterLambda));
    }

    public static void processGroupByOperations(Function function, TDSGroupBy groupByOperation, List<String> columns)
    {
        if (groupByOperation == null || (groupByOperation.getColumns().size() == 0))
        {
            return;
        }
        if (groupByOperation.getColumns().size() > 1)
        {
            throw new UnsupportedOperationException("Grouping on multiple columns is currently not supported");
        }
        Collection groupByCollection = new Collection();
        groupByCollection.values = Lists.mutable.empty();
        Collection aggregationCollection = new Collection();
        aggregationCollection.values = Lists.mutable.empty();

        // Doing groupBy only when all the rows are not expanded
        if (groupByOperation.getGroupKeys() == null || groupByOperation.getGroupKeys().size() != groupByOperation.getColumns().size())
        {
            groupByOperation.getColumns().forEach(column ->
            {
                groupByCollection.values.add(new CString(column));
            });
        }

        // Projecting the columns when there is an aggregation because that would end up projecting just the aggregation column
        if (groupByOperation.getGroupKeys().size() == groupByOperation.getColumns().size() && groupByOperation.getAggregations().size() > 0)
        {
            List<String> aggColumns = groupByOperation.getAggregations().stream().map(agg -> agg.getColumn()).collect(Collectors.toList());
            columns.forEach(column ->
            {
                if (aggColumns.contains(column) == false)
                {
                    groupByCollection.values.add(new CString(column));
                }
            });
        }
        groupByOperation.getAggregations().forEach(agg ->
        {
            AppliedFunction func = new AppliedFunction();
            func.parameters = Lists.mutable.empty();
            func.function = "agg";
            func.multiplicity = Multiplicity.PURE_ONE;
            func.parameters.add(new CString(agg.getColumn()));

            Lambda aggLamda = new Lambda();
            aggLamda.body = Lists.mutable.empty();
            aggLamda.multiplicity = Multiplicity.PURE_ONE;
            AppliedProperty property = new AppliedProperty();
            property.property = getTDSRowFunction(agg.getColumnType());
            property._class = agg.getColumnType().toString();
            Variable x = new Variable();
            x.name = DEFAULT_VARIABLE_NAME;
            property.parameters = Lists.mutable.of(x, new CString(agg.getColumn()));
            aggLamda.body.add(property);
            aggLamda.parameters = Lists.mutable.empty();
            aggLamda.parameters.add(x);
            func.parameters.add(aggLamda);

            Lambda funcLambda = new Lambda();
            funcLambda.body = Lists.mutable.empty();
            funcLambda.parameters = Lists.mutable.empty();
            funcLambda.multiplicity = Multiplicity.PURE_ONE;
            AppliedFunction aggFunc = new AppliedFunction();
            aggFunc.parameters = Lists.mutable.empty();
            aggFunc.function = agg.getFunction().getValue();
            Variable ag = new Variable();
            ag.name = "agg";
            funcLambda.body.add(aggFunc);
            aggFunc.parameters.add(ag);
            funcLambda.parameters.add(ag);
            func.parameters.add(funcLambda);
            aggregationCollection.values.add(func);
        });
        if (groupByCollection.values.size() != 0  || aggregationCollection.values.size() != 0)
        {
            updateParentFunction(function, GROUPBY_FUNCTION_NAME, Lists.mutable.of(groupByCollection, aggregationCollection));
        }
    }

    public static void processSortOperations(Function function, List<TDSSort> sortOperations)
    {
        if (sortOperations.size() == 0)
        {
            return;
        }
        Collection sortCollection = new Collection();
        sortCollection.values = Lists.mutable.empty();
        sortOperations.forEach(sortValue ->
        {
            AppliedFunction sortFunc = new AppliedFunction();
            sortFunc.parameters = Lists.mutable.empty();
            sortFunc.function = sortValue.getOrder().getValue();
            sortFunc.multiplicity = Multiplicity.PURE_ONE;
            CString var = new CString(sortValue.getColumn());
            sortFunc.parameters.add(var);
            sortCollection.values.add(sortFunc);
        });
        updateParentFunction(function, SORT_FUNCTION_NAME, Lists.mutable.of(sortCollection));
    }
}
