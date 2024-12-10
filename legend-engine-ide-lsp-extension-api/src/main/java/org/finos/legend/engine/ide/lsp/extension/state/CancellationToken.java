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

package org.finos.legend.engine.ide.lsp.extension.state;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class CancellationToken implements AutoCloseable
{
    private final String id;
    private final Consumer<String> onClose;
    private boolean cancelled = false;
    private final List<CancellationListener> cancelListeners = new ArrayList<>();

    public CancellationToken(String id, Consumer<String> onClose)
    {
        this.id = id;
        this.onClose = onClose;
    }

    public String getId()
    {
        return this.id;
    }

    public synchronized void listener(CancellationListener toRunOnCancel)
    {
        if (this.cancelled)
        {
            this.notify(toRunOnCancel);
        }
        else
        {
            this.cancelListeners.add(toRunOnCancel);
        }
    }

    public synchronized void cancel()
    {
        this.cancelled = true;
        this.cancelListeners.forEach(this::notify);
    }

    private void notify(CancellationListener runnable)
    {
        try
        {
            runnable.cancel();
        }
        catch (Throwable ignore)
        {
            // ignore
        }
    }

    @Override
    public synchronized void close()
    {
        this.onClose.accept(this.id);
    }

    public synchronized boolean isCancelled()
    {
        return this.cancelled;
    }

    public interface CancellationListener
    {
        void cancel() throws Exception;
    }
}
