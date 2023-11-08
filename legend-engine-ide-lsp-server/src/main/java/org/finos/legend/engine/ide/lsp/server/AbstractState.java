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

import org.finos.legend.engine.ide.lsp.extension.state.State;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

abstract class AbstractState implements State
{
    private final Map<String, Object> properties = new HashMap<>();
    protected final Object lock;

    AbstractState()
    {
        this.lock = this;
    }

    AbstractState(Object lock)
    {
        this.lock = Objects.requireNonNull(lock, "lock is required");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key)
    {
        synchronized (this.lock)
        {
            return (T) this.properties.get(key);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Supplier<? extends T> supplier)
    {
        synchronized (this.lock)
        {
            return (T) this.properties.computeIfAbsent(key, k -> supplier.get());
        }
    }

    @Override
    public void setProperty(String key, Object value)
    {
        synchronized (this.lock)
        {
            if (value == null)
            {
                this.properties.remove(key);
            }
            else
            {
                this.properties.put(key, value);
            }
        }
    }

    @Override
    public void removeProperty(String key)
    {
        synchronized (this.lock)
        {
            this.properties.remove(key);
        }
    }

    @Override
    public void clearProperties()
    {
        synchronized (this.lock)
        {
            this.properties.clear();
        }
    }
}
