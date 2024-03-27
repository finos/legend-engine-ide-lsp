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

package org.finos.legend.engine.ide.lsp.extension.test;

import java.util.Collections;
import java.util.List;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;

public class LegendTest
{
    private final List<String> idComponents;
    private final String id;
    private final String label;
    private final List<LegendTest> children;
    private final TextLocation location;

    private LegendTest(List<String> idComponents, List<LegendTest> children, TextLocation location)
    {
        if (idComponents.isEmpty())
        {
            throw new IllegalStateException("Requires at least 1 idComponent, found 0");
        }
        this.idComponents = Collections.unmodifiableList(idComponents);
        this.id = String.join(".", idComponents);
        this.label = idComponents.get(idComponents.size() - 1);
        this.children = Collections.unmodifiableList(children);
        this.location = location;
    }

    public List<String> getIdComponents()
    {
        return this.idComponents;
    }

    public String getId()
    {
        return this.id;
    }

    public String getLabel()
    {
        return this.label;
    }

    public List<LegendTest> getChildren()
    {
        return this.children;
    }

    public TextLocation getLocation()
    {
        return this.location;
    }

    @Override
    public String toString()
    {
        return "LegendTest{" +
                "id='" + id + '\'' +
                ", location=" + location +
                '}';
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof LegendTest))
        {
            return false;
        }
        LegendTest that = (LegendTest) o;
        return this.id.equals(that.id);
    }

    @Override
    public int hashCode()
    {
        return this.id.hashCode();
    }

    public static LegendTest newLegendTest(TextLocation location, String... idComponents)
    {
        return new LegendTest(List.of(idComponents), List.of(), location);
    }

    public static LegendTest newLegendTest(List<LegendTest> children, TextLocation location, String... idComponents)
    {
        return new LegendTest(List.of(idComponents), children, location);
    }
}
