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

import org.finos.legend.engine.ide.lsp.extension.text.LegendTextObject;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;

import java.util.Objects;

/**
 * Declaration of a Legend entity. This could be a class, enumeration, service, or any other type of model entity.
 */
public class LegendDeclaration extends LegendTextObject
{
    private final String identifier;
    private final String classifier;

    private LegendDeclaration(String identifier, String classifier, TextInterval location, TextInterval coreLocation)
    {
        super(location, coreLocation);
        this.identifier = Objects.requireNonNull(identifier, "identifier is required");
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
        return getLocation().equals(that.getLocation()) &&
                Objects.equals(getCoreLocation(), that.getCoreLocation()) &&
                this.identifier.equals(that.identifier) &&
                this.classifier.equals(that.classifier);
    }

    @Override
    public int hashCode()
    {
        int hashCode = getLocation().hashCode();
        hashCode = 7 * hashCode + Objects.hashCode(getCoreLocation());
        hashCode = 7 * hashCode + this.identifier.hashCode();
        hashCode = 7 * hashCode + this.classifier.hashCode();
        return hashCode;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName())
                .append("{id=").append(this.identifier).append(" classifier=").append(this.classifier)
                .append(" location=").append(getLocation().toCompactString());
        if (hasCoreLocation())
        {
            builder.append(" coreLocation=").append(getCoreLocation().toCompactString());
        }
        return builder.append("}").toString();
    }

    /**
     * Identifier of the declaration.
     *
     * @return declaration identifier
     */
    public String getIdentifier()
    {
        return this.identifier;
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
     * @param identifier entity name
     * @param classifier declared entity classifier
     * @param location   full location of the declaration
     * @return Legend declaration
     */
    public static LegendDeclaration newDeclaration(String identifier, String classifier, TextInterval location)
    {
        return newDeclaration(identifier, classifier, location, null);
    }

    /**
     * Create a new Legend declaration. If supplied, {@code coreLocation} should be the location of the most interesting
     * part of the declaration; e.g., the location of the identifier. It must be subsumed by {@code location}.
     *
     * @param identifier   entity name
     * @param classifier   declared entity classifier
     * @param location     full location of the declaration
     * @param coreLocation core location of the declaration (optional)
     * @return Legend declaration
     */
    public static LegendDeclaration newDeclaration(String identifier, String classifier, TextInterval location, TextInterval coreLocation)
    {
        return new LegendDeclaration(identifier, classifier, location, coreLocation);
    }
}
