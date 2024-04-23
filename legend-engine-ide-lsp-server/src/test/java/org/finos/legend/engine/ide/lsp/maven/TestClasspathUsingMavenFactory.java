// Copyright 2024 Goldman Sachs
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

package org.finos.legend.engine.ide.lsp.maven;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.InitializeParams;
import org.finos.legend.engine.ide.lsp.Constants;
import org.finos.legend.engine.ide.lsp.DummyLanguageClient;
import org.finos.legend.engine.ide.lsp.classpath.ClasspathUsingMavenFactory;
import org.finos.legend.engine.ide.lsp.classpath.SDLCPlatform;
import org.finos.legend.engine.ide.lsp.server.LegendLanguageServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestClasspathUsingMavenFactory
{
    @Test
    void loadJarsFromPom(@TempDir Path tempDir) throws Exception
    {
        String eclipseApiVersion = "10.2.0";

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/server/platforms", x ->
        {
            x.sendResponseHeaders(200, 0);
            Gson gson = new Gson();
            JsonWriter jsonWriter = gson.newJsonWriter(new OutputStreamWriter(x.getResponseBody()));
            SDLCPlatform sdlcPlatform = new SDLCPlatform();
            sdlcPlatform.setGroupId("org.eclipse.collections");
            sdlcPlatform.setName("eclipse-collections");
            sdlcPlatform.setPlatformVersion(eclipseApiVersion);
            gson.toJson(List.of(sdlcPlatform), List.class, jsonWriter);
            jsonWriter.flush();
            x.close();
        });

        httpServer.start();

        Path pom = tempDir.resolve("pom.xml");

        Files.writeString(pom,
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                        "    <modelVersion>4.0.0</modelVersion>\n" +
                        "\n" +
                        "    <groupId>org.finos.legend.engine.ide.lsp</groupId>\n" +
                        "    <artifactId>legend-engine-ide-lsp-server-sample-pom</artifactId>\n" +
                        "    <version>0.0.0-SNAPSHOT</version>\n" +
                        "\n" +
                        "    <properties>\n" +
                        "       <platform.eclipse-collections.version>0.0.0</platform.eclipse-collections.version>\n" +
                        "    </properties>\n" +
                        "    <dependencies>\n" +
                        "        <dependency>\n" +
                        "            <groupId>commons-io</groupId>\n" +
                        "            <artifactId>commons-io</artifactId>\n" +
                        "            <version>2.15.1</version>\n" +
                        "        </dependency>\n" +
                        "        <dependency>\n" +
                        "            <groupId>org.eclipse.collections</groupId>\n" +
                        "            <artifactId>eclipse-collections-api</artifactId>\n" +
                        "            <version>${platform.eclipse-collections.version}</version>\n" +
                        "        </dependency>\n" +
                        "    </dependencies>\n" +
                        "\n" +
                        "<!-- this is to test regex replacement and ensure does not get stack overflow\n" +
                        "\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean at erat magna. Praesent tincidunt interdum turpis pulvinar eleifend. Nunc ac enim diam. Nam vitae vestibulum sem, a tincidunt ligula. Cras maximus nibh ut magna porta, tristique condimentum risus luctus. Donec felis nibh, congue in diam sed, aliquam lobortis elit. In vestibulum, tortor eget rhoncus luctus, sem metus blandit orci, rutrum pellentesque ex eros in erat. Phasellus iaculis tempus venenatis. Praesent ut leo ut est ultricies mattis. Sed nisi elit, laoreet vel purus nec, tincidunt congue orci. Donec accumsan pharetra nisl, eu porta ipsum mollis id. Nam pharetra velit lectus, sed tempor arcu sodales quis. Praesent tempor lectus a felis vehicula, ac pulvinar enim ullamcorper. Curabitur dapibus facilisis pellentesque. Nam facilisis convallis tortor non semper. Pellentesque nec convallis nisl.\n" +
                        "-->\n" +
                        "</project>", StandardCharsets.UTF_8);

        LegendLanguageServer server = LegendLanguageServer.builder().synchronous().build();
        DummyLanguageClient languageClient = new DummyLanguageClient()
        {
            @Override
            public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams)
            {
                clientLog.add(String.format("configuration - %s", configurationParams.getItems().stream().map(ConfigurationItem::getSection).collect(Collectors.joining())));
                return CompletableFuture.completedFuture(configurationParams.getItems().stream().map(x ->
                {
                    switch (x.getSection())
                    {
                        case Constants.LEGEND_EXTENSIONS_OTHER_DEPENDENCIES_CONFIG_PATH:
                            JsonArray jsonElements = new JsonArray();
                            jsonElements.add("commons-lang:commons-lang:2.6");
                            jsonElements.add("commons-codec:commons-codec:1.15");
                            return jsonElements;
                        case Constants.LEGEND_SDLC_SERVER_CONFIG_PATH:
                            return new JsonPrimitive("http://localhost:" + httpServer.getAddress().getPort() + "/api");
                        default:
                            return JsonNull.INSTANCE;
                    }
                }).collect(Collectors.toList()));
            }
        };
        server.connect(languageClient);
        server.initialize(new InitializeParams());

        ClasspathUsingMavenFactory factory = new ClasspathUsingMavenFactory(pom.toFile());
        factory.initialize(server);
        ClassLoader classLoader = factory.create(Collections.emptyList()).get();

        try (URLClassLoader urlClassLoader = Assertions.assertInstanceOf(URLClassLoader.class, classLoader))
        {
            Assertions.assertEquals(5, urlClassLoader.getURLs().length);
            // from given pom
            Assertions.assertTrue(urlClassLoader.getURLs()[0].toString().endsWith("commons-io-2.15.1.jar"));
            // from given pom, version updated from platform versions
            Assertions.assertTrue(urlClassLoader.getURLs()[1].toString().endsWith("eclipse-collections-api-" + eclipseApiVersion + ".jar"));
            // added by default, as required for server to work
            Assertions.assertTrue(urlClassLoader.getURLs()[2].toString().endsWith("legend-engine-ide-lsp-default-extensions-" + server.getProjectVersion() + ".jar"));
            // from given "other dependencies" config item
            Assertions.assertTrue(urlClassLoader.getURLs()[3].toString().endsWith("commons-lang-2.6.jar"));
            // from given "other dependencies" config item
            Assertions.assertTrue(urlClassLoader.getURLs()[4].toString().endsWith("commons-codec-1.15.jar"));
        }

        String pomContent = Files.readString(pom, StandardCharsets.UTF_8);

        Assertions.assertTrue(pomContent.contains("<platform.eclipse-collections.version>" + eclipseApiVersion + "</platform.eclipse-collections.version>"));
    }
}
