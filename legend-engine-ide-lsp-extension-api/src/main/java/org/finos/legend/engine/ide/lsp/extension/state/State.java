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

package org.finos.legend.engine.ide.lsp.extension.state;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Generic state with properties that can be set.
 */
public interface State
{
    /**
     * Get the value of a property. Returns null if the property has no value.
     *
     * @param key property key
     * @param <T> value type
     * @return property value or null
     */
    <T> T getProperty(String key);

    /**
     * Get the value of a property. If property has no value, get a value from the supplier and set it as the value for
     * the property.
     *
     * @param key      property key
     * @param supplier value supplier
     * @param <T>      value type
     * @return property value or null
     */
    default <T> T getProperty(String key, Supplier<? extends T> supplier)
    {
        Objects.requireNonNull(supplier);
        T value = getProperty(key);
        if (value == null)
        {
            value = supplier.get();
            if (value != null)
            {
                setProperty(key, value);
            }
        }
        return value;
    }

    /**
     * Set the value of a property. This will overwrite any previous value that may have been set. Setting the property
     * value to null is equivalent to removing it.
     *
     * @param key   property key
     * @param value property value
     */
    void setProperty(String key, Object value);

    /**
     * Remove the value of a property. This is equivalent to {@code setProperty(key, null)}.
     *
     * @param key property key
     */
    default void removeProperty(String key)
    {
        setProperty(key, null);
    }

    /**
     * Clear all property values.
     */
    void clearProperties();

    default void logInfo(String message)
    {
        // No-op by default
    }

    default void logWarning(String message)
    {
        // No-op by default
    }

    default void logError(String message)
    {
        // No-op by default
    }
}
