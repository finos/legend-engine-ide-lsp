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

package org.finos.legend.engine.ide.lsp.server;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.MessageIssueException;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.finos.legend.engine.ide.lsp.extension.LegendMessageTracer;

public class LegendMessageTracerHandler
{
    private final LinkedBlockingQueue<Message> eventQueue = new LinkedBlockingQueue<>();
    private volatile Consumer<Message> traceImpl = this::addEventToQueue;
    private ExecutorService executorService;

    public synchronized void initialize(List<? extends LegendMessageTracer> tracers)
    {
        if (tracers.isEmpty())
        {
            this.traceImpl = this::ignoreEvent;
            this.eventQueue.clear();
            if (this.executorService != null)
            {
                this.executorService.shutdownNow();
                this.executorService = null;
            }
        }
        else
        {
            this.executorService = Executors.newSingleThreadExecutor();
            this.executorService.submit(() -> this.processEvents(tracers));
            this.traceImpl = this::addEventToQueue;
        }
    }

    private Void processEvents(List<? extends LegendMessageTracer> tracers) throws InterruptedException
    {
        Map<String, RequestMetadata> tracker = new HashMap<>();

        while (true)
        {
            Message message = this.eventQueue.take();

            if (message instanceof RequestMessage)
            {
                tracker.put(((RequestMessage) message).getId(), new RequestMetadata((RequestMessage) message, Instant.now()));
            }
            else if (message instanceof ResponseMessage)
            {
                RequestMetadata requestMetadata = tracker.remove(((ResponseMessage) message).getId());
                if (requestMetadata != null)
                {
                    LegendMessageTracer.TraceEvent traceEvent = requestMetadata.toTraceEvent((ResponseMessage) message);
                    for (LegendMessageTracer tracer : tracers)
                    {
                        try
                        {
                            tracer.trace(traceEvent);
                        }
                        catch (Exception e)
                        {
                            // todo
                        }
                    }
                }
            }
        }
    }

    private void addEventToQueue(Message message)
    {
        this.eventQueue.add(message);
    }

    private void ignoreEvent(Message message)
    {
    }

    public MessageConsumer wrap(MessageConsumer consumer)
    {
        return new LegendMessageTracerConsumer(consumer);
    }
    private class LegendMessageTracerConsumer implements MessageConsumer
    {
        private final MessageConsumer delegate;

        public LegendMessageTracerConsumer(MessageConsumer delegate)
        {
            this.delegate = delegate;
        }
        @Override
        public void consume(Message message) throws MessageIssueException, JsonRpcException
        {
            LegendMessageTracerHandler.this.traceImpl.accept(message);
            this.delegate.consume(message);
        }
    }

    private static class RequestMetadata {
        final RequestMessage request;
        final Instant start;

        private RequestMetadata(RequestMessage request, Instant start) {
            this.request = request;
            this.start = start;
        }

        private LegendMessageTracer.TraceEvent toTraceEvent(ResponseMessage response)
        {
            long elapsed = Duration.between(Instant.now(), this.start).toMillis();
            Optional<ResponseError> error = Optional.ofNullable(response.getError());
            return LegendMessageTracer.event(
                    this.request.getMethod(),
                    MessageJsonHandler.toString(request.getParams()),
                    elapsed,
                    error.map(ResponseError::getCode).orElse(null),
                    error.map(ResponseError::getMessage).orElse(null)
            );
        }
    }
}
