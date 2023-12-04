package org.finos.legend.engine.ide.lsp.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Objects;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.eclipse.collections.impl.block.procedure.checked.ThrowingProcedure;
import org.finos.legend.engine.functionActivator.api.input.FunctionActivatorInput;
import org.finos.legend.engine.functionActivator.deployment.DeploymentResult;
import org.finos.legend.engine.language.pure.relational.api.relationalElement.input.DatabaseToModelGenerationInput;
import org.finos.legend.engine.plan.execution.result.serialization.SerializationFormat;
import org.finos.legend.engine.plan.execution.stores.relational.connection.api.schema.model.DatabaseBuilderConfig;
import org.finos.legend.engine.plan.execution.stores.relational.connection.api.schema.model.DatabaseBuilderInput;
import org.finos.legend.engine.plan.execution.stores.relational.connection.api.schema.model.TargetDatabase;
import org.finos.legend.engine.protocol.functionActivator.metamodel.DeploymentConfiguration;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContext;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.ExecutionPlan;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.connection.RelationalDatabaseConnection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Database;
import org.finos.legend.engine.shared.core.ObjectMapperFactory;

public class LegendServerClient
{
    private final ObjectMapper mapper = ObjectMapperFactory.getNewStandardObjectMapperWithPureProtocolExtensionSupports();
    private final HttpClient client;
    private final String serverUrl;

    public LegendServerClient(HttpClient client, String serverUrl)
    {
        this.client = client;
        this.serverUrl = serverUrl;
    }

    public <T extends DeploymentResult> T functionActivatorPublishToSandbox(String clientVersion,
                                                                             String functionActivatorPath,
                                                                             DeploymentConfiguration config,
                                                                             PureModelContext model,
                                                                             Class<T> resultClass
    ) throws Exception
    {
        // defined on FunctionActivatorAPI.class
        FunctionActivatorInput input = new FunctionActivatorInput(clientVersion, functionActivatorPath, model, config);
        try (InputStream is = this.executeHttpPost("/functionActivator/publishToSandbox", input))
        {
            return this.mapper.readValue(is, resultClass);
        }
    }

    public void executePlan(ExecutionPlan executionPlan, SerializationFormat serializationFormat, ThrowingProcedure<InputStream> resultConsumer) throws Exception
    {
        // defined on ExecutePlanStrategic.class
        try (InputStream is = this.executeHttpPost("/executionPlan/v1/execution/executePlan?serializationFormat=" + serializationFormat, executionPlan))
        {
            resultConsumer.safeValue(is);
        }
    }

    public Database generateDatabaseFromConnection(RelationalDatabaseConnection connection, TargetDatabase targetDatabase, DatabaseBuilderConfig config) throws Exception
    {
        // defined on SchemaExplorationApi.class
        DatabaseBuilderInput input = new DatabaseBuilderInput();
        input.connection = connection;
        input.targetDatabase = targetDatabase;
        input.config = config;

        try (InputStream is = this.executeHttpPost("pure/v1/utilities/database/schemaExploration", input))
        {
            PureModelContextData pmcd = this.mapper.readValue(is, PureModelContextData.class);
            return pmcd.getElementsOfType(Database.class).stream().filter(x -> Objects.equals(x._package, targetDatabase._package) && Objects.equals(x.name, targetDatabase.name)).findAny().orElseThrow();
        }
    }

    public PureModelContextData generateModelFromDatabase(String databasePath, String targetPackage, PureModelContextData model) throws Exception
    {
        // defined on RelationalElementAPI.class
        DatabaseToModelGenerationInput input = new DatabaseToModelGenerationInput(databasePath, model, targetPackage);
        try (InputStream is = this.executeHttpPost("/pure/v1/relational/generateModelsFromDatabaseSpecification", input))
        {
            return this.mapper.readValue(is, PureModelContextData.class);
        }
    }

    private InputStream executeHttpPost(String url, Object entity) throws Exception
    {
        HttpPost httpPost = new HttpPost(serverUrl + url);
        httpPost.setEntity(new StringEntity(mapper.writeValueAsString(entity), ContentType.APPLICATION_JSON));
        HttpResponse response = this.client.execute(httpPost);
        if (response.getStatusLine().getStatusCode() >= 300)
        {
            String msg = response.getStatusLine().getReasonPhrase();
            if (response.getEntity() != null)
            {
                msg = EntityUtils.toString(response.getEntity());
            }
            throw new HttpResponseException(response.getStatusLine().getStatusCode(), msg);
        }

        return response.getEntity().getContent();
    }
}
