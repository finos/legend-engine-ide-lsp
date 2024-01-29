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

package org.finos.legend.engine.ide.lsp.extension.reference;

import java.util.Objects;
import org.finos.legend.engine.ide.lsp.extension.text.LegendTextObject;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;

/**
 * Legend reference. This track the location where a pointer to another element exits, and the referenced element's location.
 * This allows navigation from the given reference to the referenced location.
 */
public class LegendReference extends LegendTextObject
{
    private final TextLocation referencedLocation;

    private LegendReference(TextLocation referenceLocation, TextLocation coreReferenceLocation, TextLocation referencedLocation)
    {
        super(referenceLocation, coreReferenceLocation);
        this.referencedLocation = Objects.requireNonNull(referencedLocation, "referenced location is required");
    }

    /**
     * The referenced element location.
     * @return the location of the element been referenced by this reference
     */
    public TextLocation getReferencedLocation()
    {
        return referencedLocation;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Builder for {@link LegendReference}.
     */
    public static class Builder extends AbstractBuilder<Builder>
    {
        private TextLocation referencedLocation;

        private Builder()
        {

        }

        @Override
        protected Builder self()
        {
            return this;
        }

        /**
         * Location of the referenced element
         *
         * @param referencedLocation referenced element's location
         * @return the current builder with the new referenced location set
         */
        public Builder withReferencedLocation(TextLocation referencedLocation)
        {
            this.referencedLocation = referencedLocation;
            return this;
        }

        public LegendReference build()
        {
            return new LegendReference(this.getLocation(), this.getCoreLocation(), this.referencedLocation);
        }
    }
}
