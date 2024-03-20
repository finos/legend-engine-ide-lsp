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

package org.finos.legend.engine.ide.lsp.extension;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Defines the contract for event consumption, allowing custom event tracking that fit different environments
 */
public interface LegendUsageEventConsumer extends LegendLSPFeature
{
    /**
     * Consume the given event message
     * @param event to consume
     */
    void consume(LegendUsageEvent event);

    /**
     * Create a new event
     * @param eventType the type of event
     * @param startTime when the event started
     * @param endTime when the event completed
     * @param metadata metadata for the event
     * @return newly created event
     */
    static LegendUsageEvent event(String eventType, Instant startTime, Instant endTime, Map<String, Object> metadata)
    {
        return new LegendUsageEvent(startTime, endTime, eventType, metadata);
    }

    final class LegendUsageEvent
    {
        private final Instant startTime;
        private final Instant endTime;
        private final String eventType;
        private final Map<String, Object> metadata;

        private LegendUsageEvent(Instant startTime, Instant endTime, String eventType, Map<String, Object> metadata)
        {
            this.startTime = startTime;
            this.endTime = endTime;
            this.eventType = eventType;
            this.metadata = Collections.unmodifiableMap(metadata);
        }

        /**
         * The Instant when the event started
         * @return start time of event
         */
        public Instant getStartTime()
        {
            return this.startTime;
        }

        /**
         * The Instant when the event ended
         * @return end time of event
         */
        public Instant getEndTime()
        {
            return this.endTime;
        }

        /**
         * The event type
         * @return event type
         */
        public String getEventType()
        {
            return this.eventType;
        }

        /**
         * metadata of the event
         * @return metadata of the event
         */
        public Map<String, Object> getMetadata()
        {
            return this.metadata;
        }

        /**
         * How long the event took, in milliseconds
         * @return elapsed, in ms
         */
        public long elapsedMillis()
        {
            return this.endTime.toEpochMilli() - this.startTime.toEpochMilli();
        }

        @Override
        public String toString()
        {
            return "LegendUsageEvent{" +
                    "startTime=" + startTime +
                    ", endTime=" + endTime +
                    ", eventType='" + eventType + '\'' +
                    ", metadata=" + metadata +
                    '}';
        }
    }
}
