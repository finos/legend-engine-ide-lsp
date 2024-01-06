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

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.finos.legend.engine.ide.lsp.DummyLanguageClient;
import org.finos.legend.engine.ide.lsp.classpath.ClasspathUsingMavenFactory;
import org.finos.legend.engine.ide.lsp.server.LegendLanguageServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestClasspathUsingMavenFactory
{
    @Test
    void loadJarsFromPom(@TempDir Path tempDir) throws Exception
    {
        Path pom = tempDir.resolve("pom.xml");

        Files.writeString(pom,
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                        "    <modelVersion>4.0.0</modelVersion>\n" +
                        "\n" +
                        "    <groupId>org.finos.legend.engine.ide.lsp</groupId>\n" +
                        "    <artifactId>legend-engine-ide-lsp-server-sample-pom</artifactId>\n" +
                        "    <version>0.0.0-SNAPSHOT</version>\n" +
                        "\n" +
                        "    <dependencies>\n" +
                        "        <dependency>\n" +
                        "            <groupId>commons-io</groupId>\n" +
                        "            <artifactId>commons-io</artifactId>\n" +
                        "            <version>2.15.1</version>\n" +
                        "        </dependency>\n" +
                        "    </dependencies>\n" +
                        "</project>", StandardCharsets.UTF_8);

        LegendLanguageServer server = LegendLanguageServer.builder().synchronous().build();
        DummyLanguageClient languageClient = new DummyLanguageClient();
        server.connect(languageClient);

        ClasspathUsingMavenFactory factory = new ClasspathUsingMavenFactory(pom.toFile());
        factory.initialize(server);
        ClassLoader classLoader = factory.create(Collections.emptyList()).get();

        try (URLClassLoader urlClassLoader = Assertions.assertInstanceOf(URLClassLoader.class, classLoader))
        {
            Assertions.assertEquals(1, urlClassLoader.getURLs().length);
            Assertions.assertTrue(urlClassLoader.getURLs()[0].toString().endsWith("commons-io-2.15.1.jar"));
        }
    }
}
