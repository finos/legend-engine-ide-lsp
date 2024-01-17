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

package org.finos.legend.engine.ide.lsp.server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.finos.legend.engine.ide.lsp.extension.state.State;

abstract class AbstractState implements State
{
    private final ConcurrentMap<String, Object> properties = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key)
    {
        return (T) this.properties.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Supplier<? extends T> supplier)
    {
        return (T) this.properties.computeIfAbsent(key, k -> supplier.get());
    }

    @Override
    public void setProperty(String key, Object value)
    {
        this.properties.compute(key, (x, y) -> value);
    }

    @Override
    public void removeProperty(String key)
    {
        this.properties.remove(key);
    }

    @Override
    public void clearProperties()
    {
        this.properties.clear();
    }
}
