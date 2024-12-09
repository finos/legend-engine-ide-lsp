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

package org.finos.legend.engine.ide.lsp.extension;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.eclipse.collections.impl.block.function.checked.ThrowingFunction;

class DefaultLegendEngineServerClient implements LegendEngineServerClient
{
    private String getEngineServerUrl()
    {
        return System.getProperty("legend.engine.server.url");
    }

    @Override
    public boolean isServerConfigured()
    {
        return this.getEngineServerUrl() != null;
    }

    @Override
    public <T> T post(String path, String payload, String contentType, ThrowingFunction<InputStream, T> processor, Consumer<Runnable> cancelListener)
    {
        return post(path, new StringEntity(payload, ContentType.getByMimeType(contentType)), processor, cancelListener);
    }

    private <T> T post(String path, HttpEntity payload, ThrowingFunction<InputStream, T> processor, Consumer<Runnable> cancelListener)
    {
        if (!isServerConfigured())
        {
            throw new IllegalStateException("Engine server is not configured");
        }
        String url = buildUrl(path);
        try
        {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setEntity(payload);

            cancelListener.accept(httpPost::abort);

            try (CloseableHttpClient client = HttpClientBuilder.create().build();
                 CloseableHttpResponse response = client.execute(httpPost))
            {
                HttpEntity entity = response.getEntity();
                if ((response.getStatusLine().getStatusCode() / 100) != 2)
                {
                    String responseString = EntityUtils.toString(entity);
                    throw new RuntimeException("Engine server responded to " + url + " with status: " + response.getStatusLine().getStatusCode() + "\nresponse: " + responseString);
                }
                try (InputStream content = entity.getContent())
                {
                    return processor.safeValueOf(content);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private String buildUrl(String path)
    {
        return this.getEngineServerUrl() +
                (this.getEngineServerUrl().endsWith("/") ?
                 (path.startsWith("/") ? path.substring(1) : path) :
                 (path.startsWith("/") ? path : ("/" + path)));
    }
}
