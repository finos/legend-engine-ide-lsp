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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;

public class CompileResult extends AbstractLSPGrammarExtension.Result<PureModel>
{
    private final PureModelContextData pureModelContextData;
    private final ImmutableList<EngineException> engineExceptions;

    CompileResult(PureModel pureModel, PureModelContextData pureModelContextData)
    {
        super(pureModel, null);
        this.pureModelContextData = pureModelContextData;
        this.engineExceptions = pureModel.getEngineExceptions();
    }

    CompileResult(EngineException e, PureModelContextData pureModelContextData)
    {
        super(null, e);
        this.pureModelContextData = pureModelContextData;
        this.engineExceptions = Lists.immutable.empty();
    }

    public PureModel getPureModel()
    {
        return getResult();
    }

    public PureModelContextData getPureModelContextData()
    {
        return this.pureModelContextData;
    }

    @Override
    public boolean hasEngineException()
    {
        return !engineExceptions.isEmpty() || super.hasEngineException();
    }

    public ImmutableList<EngineException> getEngineExceptions()
    {
        if (!engineExceptions.isEmpty())
        {
            return this.engineExceptions;
        }
        return Lists.immutable.with(getEngineException());
    }

    public RuntimeException getCompileErrorResult()
    {
        return new RuntimeException("Cannot execute action due to compilation failure(s)! Check diagnostics for more details.");
    }
}
