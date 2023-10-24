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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Legend declaration. This could be a class, enumeration, service, or any other type of model entity. It could also be
 * a child declaration, such as a property or enum value.
 */
public class LegendDeclaration extends LegendTextObject
{
    private final String identifier;
    private final String classifier;
    private final List<LegendDeclaration> children;

    private LegendDeclaration(String identifier, String classifier, TextInterval location, TextInterval coreLocation, List<LegendDeclaration> children)
    {
        super(location, coreLocation);
        this.identifier = Objects.requireNonNull(identifier, "identifier is required");
        this.classifier = Objects.requireNonNull(classifier, "classifier is required");
        this.children = children;
        children.forEach(c ->
        {
            if (!getLocation().subsumes(c.getLocation(), true))
            {
                throw new IllegalArgumentException("Location of declaration (" + getLocation() + ") must strictly subsume the location of all children: " + c);
            }
        });
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
                this.classifier.equals(that.classifier) &&
                this.children.equals(that.children);
    }

    @Override
    public int hashCode()
    {
        int hashCode = getLocation().hashCode();
        hashCode = 7 * hashCode + Objects.hashCode(getCoreLocation());
        hashCode = 7 * hashCode + this.identifier.hashCode();
        hashCode = 7 * hashCode + this.classifier.hashCode();
        hashCode = 7 * hashCode + this.children.hashCode();
        return hashCode;
    }

    @Override
    public String toString()
    {
        return appendString(new StringBuilder(getClass().getSimpleName())).toString();
    }

    private StringBuilder appendString(StringBuilder builder)
    {
        builder.append("{id=").append(this.identifier).append(" classifier=").append(this.classifier)
                .append(" location=").append(getLocation().toCompactString());
        if (hasCoreLocation())
        {
            builder.append(" coreLocation=").append(getCoreLocation().toCompactString());
        }
        if (hasChildren())
        {
            builder.append(" children=[");
            int len = builder.length();
            this.children.forEach(c -> c.appendString((builder.length() == len) ? builder : builder.append(", ")));
            builder.append("]");
        }
        return builder.append("}");
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
     * Whether this declaration has child declarations.
     *
     * @return whether there are children
     */
    public boolean hasChildren()
    {
        return !this.children.isEmpty();
    }

    /**
     * Get any children of this declaration. These are
     *
     * @return child declarations
     */
    public List<LegendDeclaration> getChildren()
    {
        return this.children;
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
        return builder().withIdentifier(identifier).withClassifier(classifier).withLocation(location).build();
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
        return builder().withIdentifier(identifier).withClassifier(classifier).withLocation(location).withCoreLocation(coreLocation).build();
    }

    /**
     * Create a new {@link LegendDeclaration} builder.
     *
     * @return Legend declaration builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Builder for {@link LegendDeclaration}.
     */
    public static class Builder
    {
        private TextInterval location;
        private TextInterval coreLocation;
        private String identifier;
        private String classifier;
        private final List<LegendDeclaration> children = new ArrayList<>();

        /**
         * Set the location and return this builder.
         *
         * @param location declaration location
         * @return this builder
         */
        public Builder withLocation(TextInterval location)
        {
            this.location = location;
            return this;
        }

        /**
         * Set the core location and return this builder.
         *
         * @param coreLocation core location
         * @return this builder
         */
        public Builder withCoreLocation(TextInterval coreLocation)
        {
            this.coreLocation = coreLocation;
            return this;
        }

        /**
         * Set the identifier and return this builder.
         *
         * @param identifier identifier
         * @return this builder
         */
        public Builder withIdentifier(String identifier)
        {
            this.identifier = identifier;
            return this;
        }

        /**
         * Set the classifier and return this builder.
         *
         * @param classifier classifier
         * @return this builder
         */
        public Builder withClassifier(String classifier)
        {
            this.classifier = classifier;
            return this;
        }

        /**
         * Add a child declaration and return this builder.
         *
         * @param child child declaration
         * @return this builder
         */
        public Builder withChild(LegendDeclaration child)
        {
            this.children.add(Objects.requireNonNull(child, "child may not be null"));
            return this;
        }

        /**
         * Add child declarations and return this builder.
         *
         * @param children child declarations
         * @return this builder
         */
        public Builder withChildren(Iterable<? extends LegendDeclaration> children)
        {
            children.forEach(this::withChild);
            return this;
        }

        /**
         * Build the {@link LegendDeclaration}.
         *
         * @return Legend declaration
         */
        public LegendDeclaration build()
        {
            return new LegendDeclaration(this.identifier, this.classifier, this.location, this.coreLocation, this.children.isEmpty() ? Collections.emptyList() : List.copyOf(this.children));
        }
    }
}
