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

import java.util.Optional;
import java.util.stream.Stream;
import org.finos.legend.engine.ide.lsp.extension.LegendReferenceResolver;
import org.finos.legend.engine.protocol.pure.m3.PackageableElement;
import org.finos.legend.engine.protocol.pure.m3.extension.Profile;
import org.finos.legend.engine.protocol.pure.m3.function.Function;
import org.finos.legend.engine.protocol.pure.m3.relationship.Association;
import org.finos.legend.engine.protocol.pure.m3.type.Class;
import org.finos.legend.engine.protocol.pure.m3.type.Enumeration;
import org.finos.legend.engine.protocol.pure.m3.type.Measure;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElementVisitor;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.PackageableConnection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.data.DataElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.PackageableRuntime;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.section.SectionIndex;

public class PackageableElementDefaultVisitor implements PackageableElementVisitor<Stream<Optional<LegendReferenceResolver>>>
{
    @Override
    public Stream<Optional<LegendReferenceResolver>> visit(PackageableElement packageableElement)
    {
        return Stream.empty();
    }

    @Override
    public Stream<Optional<LegendReferenceResolver>> visit(Profile profile)
    {
        return Stream.empty();
    }

    @Override
    public Stream<Optional<LegendReferenceResolver>> visit(Enumeration enumeration)
    {
        return Stream.empty();
    }

    @Override
    public Stream<Optional<LegendReferenceResolver>> visit(Class aClass)
    {
        return Stream.empty();
    }

    @Override
    public Stream<Optional<LegendReferenceResolver>> visit(Association association)
    {
        return Stream.empty();
    }

    @Override
    public Stream<Optional<LegendReferenceResolver>> visit(Function function)
    {
        return Stream.empty();
    }

    @Override
    public Stream<Optional<LegendReferenceResolver>> visit(Measure measure)
    {
        return Stream.empty();
    }

    @Override
    public Stream<Optional<LegendReferenceResolver>> visit(SectionIndex sectionIndex)
    {
        return Stream.empty();
    }

    @Override
    public Stream<Optional<LegendReferenceResolver>> visit(Mapping mapping)
    {
        return Stream.empty();
    }

    @Override
    public Stream<Optional<LegendReferenceResolver>> visit(PackageableRuntime packageableRuntime)
    {
        return Stream.empty();
    }

    @Override
    public Stream<Optional<LegendReferenceResolver>> visit(PackageableConnection packageableConnection)
    {
        return Stream.empty();
    }

    @Override
    public Stream<Optional<LegendReferenceResolver>> visit(DataElement dataElement)
    {
        return Stream.empty();
    }
}
