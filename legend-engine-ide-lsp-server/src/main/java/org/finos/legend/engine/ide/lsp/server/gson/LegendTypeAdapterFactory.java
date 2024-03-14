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

package org.finos.legend.engine.ide.lsp.server.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSRequest;

/**
 * The default LSP Gson processing only accept enums that are ser/deser as integers, while we want enums as strings
 * in some places.  This uses a vanilla gson for processing, rather than LSP gson instance
 */
public class LegendTypeAdapterFactory implements TypeAdapterFactory
{
    private final Gson vanillaGson = new Gson();

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
    {
        return new LegendTypeAdapter<>(this.vanillaGson, type);
    }

    private static class LegendTypeAdapter<T> extends TypeAdapter<T>
    {
        private final Gson gson;
        private final TypeToken<T> type;

        public LegendTypeAdapter(Gson gson, TypeToken<T> type)
        {
            this.gson = gson;
            this.type = type;
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException
        {
            this.gson.toJson(value, TDSRequest.class, out);
        }

        @Override
        public T read(JsonReader in) throws IOException
        {
            return this.gson.fromJson(in, this.type);
        }
    }
}
