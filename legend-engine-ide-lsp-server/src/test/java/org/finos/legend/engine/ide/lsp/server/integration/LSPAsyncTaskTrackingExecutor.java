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

package org.finos.legend.engine.ide.lsp.server.integration;

import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.jsonrpc.json.ConcurrentMessageProcessor;

/**
 * A {@link ThreadPoolExecutor} that uses a {@link Phaser} to track submitted task and completed tasks.
 * <pl/>
 * The pool {@link Phaser#register()} a new party on the phaser when a task is submitted,
 * and then {@link Phaser#arriveAndDeregister()} the phaser when the task finish running, even if finishes exceptionally.
 * <pl/>
 * This allows the test cases to wait on the phaser for all task to complete before doing assertions.
 * <pl/>
 * <pl/>
 * This thread pool also understand that the LSP launcher submit long-running tasks, and ignore them from tracking
 */
class LSPAsyncTaskTrackingExecutor extends ThreadPoolExecutor
{
    private final Phaser phaser;

    public LSPAsyncTaskTrackingExecutor(Phaser phaser)
    {
        super(4, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        this.phaser = phaser;
    }

    protected <T> java.util.concurrent.RunnableFuture<T> newTaskFor(Runnable runnable, T value)
    {
        if (runnable instanceof ConcurrentMessageProcessor)
        {
            // if is the long-running task of the LSP launchers
            // as this will never finish and prevent us from sync with the test cases
            return new ConcurrentMessageProcessorFutureTask<>((ConcurrentMessageProcessor) runnable, value);
        }

        return super.newTaskFor(runnable, value);
    }

    @Override
    public void execute(Runnable command)
    {
        // if is the long-running task of the LSP launchers, process normally
        // otherwise track to help with synchronizing on the test cases
        if (command instanceof ConcurrentMessageProcessorFutureTask)
        {
            super.execute(command);
        }
        else
        {
            this.phaser.register();
            super.execute(new TrackingRunnable(command));
        }
    }

    private class TrackingRunnable implements Runnable
    {
        private final Runnable delegate;

        public TrackingRunnable(Runnable delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void run()
        {
            try
            {
                delegate.run();
            }
            finally
            {
                LSPAsyncTaskTrackingExecutor.this.phaser.arriveAndDeregister();
            }
        }
    }

    private static class ConcurrentMessageProcessorFutureTask<T> extends FutureTask<T>
    {
        public ConcurrentMessageProcessorFutureTask(ConcurrentMessageProcessor runnable, T result)
        {
            super(runnable, result);
        }
    }
}
