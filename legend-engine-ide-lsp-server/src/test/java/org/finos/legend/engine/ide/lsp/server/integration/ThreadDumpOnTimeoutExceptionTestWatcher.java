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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * Given the multi-thread nature of the LPS server implementation, there are cases a test might timeout while waiting for
 * an asynchronous tasks.
 * </p>
 * This test watcher monitors for test cases that fail with {@link TimeoutException}, and print a thread dump.
 * The goal of the thread dump is to help identify potential thread deadlock scenarios.
 */
class ThreadDumpOnTimeoutExceptionTestWatcher implements TestWatcher
{
    @Override
    public void testAborted(ExtensionContext context, Throwable cause)
    {
        this.threadDumpIfTimeoutException(cause);
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause)
    {
        this.threadDumpIfTimeoutException(cause);
    }

    private void threadDumpIfTimeoutException(Throwable e)
    {
        if (e instanceof TimeoutException)
        {
            String threadDump = Stream.of(ManagementFactory.getThreadMXBean().dumpAllThreads(true, true))
                    .map(ThreadInfo::toString)
                    .collect(Collectors.joining("\n"));
            System.out.println("###############################################################");
            System.out.println("FUTURE TASK TIMEOUT - THERE COULD BE A THREAD DEADLOCK - THREAD DUMP");
            System.out.println();
            System.out.println(threadDump);
            System.out.println();
            System.out.println("###############################################################");
        }
    }
}
