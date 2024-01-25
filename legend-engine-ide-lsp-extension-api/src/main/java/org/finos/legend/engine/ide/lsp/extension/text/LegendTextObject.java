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

package org.finos.legend.engine.ide.lsp.extension.text;

import java.util.Objects;

/**
 * <p>Legend text object. This represents anything of significance in Legend text. It could be a class declaration or a
 * variable in a function or anything else of interest.</p>
 * <br>
 * <p>All Legend text objects have a location, which is a continuous interval of text ({@link TextInterval}). Some may
 * additionally have a "core" location, which is a sub-interval that indicates the most interesting part. For example,
 * if the object is a function definition, the core location could be the location of the function name.</p>
 */
public abstract class LegendTextObject implements Locatable
{
    private final TextLocation location;
    private final TextLocation coreLocation;

    /**
     * Construct a Legend text object with a location and optional "core" location. If present, the core location must
     * be a sub-interval of the full location.
     *
     * @param location     object location (required)
     * @param coreLocation "core" location (optional)
     */
    protected LegendTextObject(TextLocation location, TextLocation coreLocation)
    {
        this.location = Objects.requireNonNull(location, "location is required");
        this.coreLocation = coreLocation;
        if ((coreLocation != null) && !location.subsumes(coreLocation))
        {
            throw new IllegalArgumentException("Full location " + location + " must subsume core location " + coreLocation);
        }
    }

    /**
     * Construct a Legend text object with a location but no "core" location.
     *
     * @param location object location
     */
    protected LegendTextObject(TextLocation location)
    {
        this(location, null);
    }

    /**
     * Location of the object. This is the full location, from start to end.
     *
     * @return location
     */
    @Override
    public TextLocation getLocation()
    {
        return this.location;
    }

    /**
     * Whether this has a core location. {@link #getCoreLocation} will return a non-null value if and only if this
     * returns true.
     *
     * @return whether this has a core location
     * @see #getCoreLocation
     */
    public boolean hasCoreLocation()
    {
        return this.coreLocation != null;
    }

    /**
     * Optional "core" location of the object. This is the location of the most interesting part of the object.
     * For example, it could be the location of a function or class name. If it is non-null, it will be a sub-interval
     * of the interval returned by {@link #getLocation}.
     *
     * @return core location or null
     * @see #hasCoreLocation
     * @see #getLocation
     */
    public TextLocation getCoreLocation()
    {
        return this.coreLocation;
    }
}
