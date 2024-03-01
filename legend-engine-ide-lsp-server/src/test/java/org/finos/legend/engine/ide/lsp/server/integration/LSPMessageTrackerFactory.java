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

import java.io.PrintWriter;
import java.time.Clock;
import java.util.HashMap;
import java.util.concurrent.Phaser;
import java.util.function.Function;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.MessageIssueException;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.TracingMessageConsumer;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.validation.ReflectiveMessageValidator;
import org.junit.jupiter.api.Assertions;

/**
 * A factory to create {@link MessageConsumer}s that leverage a {@link Phaser} to track submitted and received LSP JRPC messages.
 * <pl/>
 * The created consumer, when a message is being sent to remote will {@link Phaser#register()}
 * to notify of a message that is pending processing.
 * <pl/>
 * The consumer then, when a message is being processed by the remote, will {@link Phaser#arriveAndDeregister()},
 * even if processing completes exceptionally
 * <pl/>
 * This allows the test cases to wait on the phaser for all messages been acknowledged and processed before doing assertions.
 * <pl/>
 * <pl/>
 * This factory also crates consumers that will validate and trace all sent and received messages.
 */
class LSPMessageTrackerFactory
{
    private final Phaser phaser;

    public LSPMessageTrackerFactory(Phaser phaser)
    {
        this.phaser = phaser;
    }

    public Function<MessageConsumer, MessageConsumer> create()
    {
        HashMap<String, TracingMessageConsumer.RequestMetadata> receivedRequests = new HashMap<>();
        HashMap<String, TracingMessageConsumer.RequestMetadata> sentRequests = new HashMap<>();
        return original -> new LSPMessageConsumerWithTracking(original, sentRequests, receivedRequests);
    }

    private class LSPMessageConsumerWithTracking implements MessageConsumer
    {
        private final MessageConsumer originalMc;
        private final MessageConsumer delegateMc;

        public LSPMessageConsumerWithTracking(MessageConsumer originalMc,
                                              HashMap<String, TracingMessageConsumer.RequestMetadata> sentRequests,
                                              HashMap<String, TracingMessageConsumer.RequestMetadata> receivedRequests
        )
        {
            Assertions.assertNotNull(originalMc, "Missing original consumer");
            Assertions.assertTrue(originalMc instanceof StreamMessageConsumer
                    || originalMc instanceof RemoteEndpoint, "Cannot track as original consumer is not supported: " + originalMc.getClass().getName());

            this.originalMc = originalMc;

            MessageConsumer tracer = new TracingMessageConsumer(
                    originalMc,
                    sentRequests,
                    receivedRequests,
                    new PrintWriter(System.out),
                    Clock.systemDefaultZone()
            );

            this.delegateMc = new ReflectiveMessageValidator(tracer);
        }

        @Override
        public void consume(Message message) throws MessageIssueException, JsonRpcException
        {
            // sending a message, then register on phaser
            if (this.originalMc instanceof StreamMessageConsumer)
            {
                LSPMessageTrackerFactory.this.phaser.register();
            }

            try
            {
                this.delegateMc.consume(message);
            }
            finally
            {
                // receiving a message, then arrive at phaser
                if (this.originalMc instanceof RemoteEndpoint)
                {
                    LSPMessageTrackerFactory.this.phaser.arriveAndDeregister();
                }
            }
        }
    }
}
