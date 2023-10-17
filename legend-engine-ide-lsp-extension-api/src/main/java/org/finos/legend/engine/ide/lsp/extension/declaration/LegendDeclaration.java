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

package org.finos.legend.engine.ide.lsp.extension.declaration;

import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;

import java.util.Objects;

/**
 * Declaration of a Legend entity. This could be a class, enumeration, service, or any other type of model entity.
 */
public class LegendDeclaration
{
    private final TextInterval location;
    private final String classifier;

    private LegendDeclaration(TextInterval location, String classifier)
    {
        this.location = Objects.requireNonNull(location, "location is required");
        this.classifier = Objects.requireNonNull(classifier, "classifier is required");
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof LegendDeclaration))
        {
            return false;
        }

        LegendDeclaration that = (LegendDeclaration) other;
        return this.location.equals(that.location) && this.classifier.equals(that.classifier);
    }

    @Override
    public int hashCode()
    {
        return this.location.hashCode() + (7 * this.classifier.hashCode());
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{location=" + this.location.toCompactString() + " classifier=" + this.classifier + "}";
    }

    /**
     * Location of the declaration.
     *
     * @return declaration location
     */
    public TextInterval getLocation()
    {
        return this.location;
    }

    /**
     * Classifier (type) of the declaration.
     *
     * @return declaration classifier
     */
    public String getClassifier()
    {
        return this.classifier;
    }


    /**
     * Create a new Legend declaration.
     *
     * @param location   declaration location
     * @param classifier declared entity classifier
     * @return new Legend declaration
     */
    public static LegendDeclaration newDeclaration(TextInterval location, String classifier)
    {
        return new LegendDeclaration(location, classifier);
    }
}
