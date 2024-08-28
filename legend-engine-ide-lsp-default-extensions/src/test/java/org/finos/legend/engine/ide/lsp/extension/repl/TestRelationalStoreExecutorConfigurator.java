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

package org.finos.legend.engine.ide.lsp.extension.repl;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.finos.legend.engine.ide.lsp.extension.PlanExecutorConfigurator;
import org.finos.legend.engine.ide.lsp.extension.relational.RelationalStoreExecutorConfigurator;
import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.plan.execution.stores.StoreType;
import org.finos.legend.engine.plan.execution.stores.relational.config.RelationalExecutionConfiguration;
import org.finos.legend.engine.plan.execution.stores.relational.connection.authentication.strategy.OAuthProfile;
import org.finos.legend.engine.plan.execution.stores.relational.plugin.RelationalStoreState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestRelationalStoreExecutorConfigurator
{
    @Test
    public void testBuildRelationalStoreExecutorConfigurator(@TempDir Path dir) throws Exception
    {
        Path planExecutorConfigurationJsonPath = dir.resolve("planExecutorConfiguration.json");
        try (InputStream is = Objects.requireNonNull(TestRelationalStoreExecutorConfigurator.class.getResourceAsStream("/planExecutorConfiguration.json"));
             OutputStream os = Files.newOutputStream(planExecutorConfigurationJsonPath, StandardOpenOption.CREATE)
        )
        {
            is.transferTo(os);
        }
        PlanExecutor actualPlanExecutor = PlanExecutorConfigurator.create(planExecutorConfigurationJsonPath, List.of(RelationalStoreExecutorConfigurator.class.getDeclaredConstructor().newInstance()));
        Assertions.assertTrue(actualPlanExecutor.getExecutorsOfType(StoreType.Relational)
                .stream()
                .map(rse -> ((RelationalStoreState) rse.getStoreState()).getRelationalExecutor().getRelationalExecutionConfiguration())
                .anyMatch(this::isEqual), "Assertion failure: the actual configuration does not match the expected configuration!");
    }

    private boolean isEqual(RelationalExecutionConfiguration actualConfig)
    {
        Integer expectedPort = 1975;
        String expectedTempPath = "/test/path/repl";
        String expectedOAuthProfileKey = "testProfileKey";
        String expectedOAuthProfileDiscoveryUrl = "https://test/discoveryUrl";
        String expectedOAuthProfileClientId = "testClientId";
        Map<String, String> expectedCustomParams = Map.of("testKey", "testValue");
        List<OAuthProfile> actualOAuthProfiles = actualConfig.oauthProfiles;
        return expectedPort.equals(actualConfig.temporarytestdb.port)
                && expectedTempPath.equals(actualConfig.tempPath)
                && actualOAuthProfiles.size() == 1
                && expectedOAuthProfileKey.equals(actualOAuthProfiles.get(0).key)
                && expectedOAuthProfileDiscoveryUrl.equals(actualOAuthProfiles.get(0).discoveryUrl)
                && expectedOAuthProfileClientId.equals(actualOAuthProfiles.get(0).clientId)
                && expectedCustomParams.equals(actualOAuthProfiles.get(0).customParams);
    }
}
