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

package org.finos.legend.engine.ide.lsp.extension.execution;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.finos.legend.engine.ide.lsp.extension.text.Locatable;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;

/**
 * A Legend command which can be executed.
 */
public class LegendCommand implements Locatable
{
    private final String entity;
    private final String id;
    private final String title;
    private final TextLocation location;
    private final Map<String, String> executableArgs;
    private final Map<String, LegendInputParamter> inputParameters;

    private LegendCommand(String entity, String id, String title, TextLocation location, Map<String, String> executableArgs, Map<String, LegendInputParamter> inputParameters)
    {
        this.entity = Objects.requireNonNull(entity, "entity is required");
        this.id = Objects.requireNonNull(id, "commandId is required");
        this.title = Objects.requireNonNull(title, "title is required");
        this.location = Objects.requireNonNull(location, "location is required");
        this.executableArgs = (executableArgs == null) ? Collections.emptyMap() : Collections.unmodifiableMap(executableArgs);
        this.inputParameters = (inputParameters == null) ? Collections.emptyMap() : Collections.unmodifiableMap(inputParameters);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof LegendCommand))
        {
            return false;
        }

        LegendCommand that = (LegendCommand) other;
        return this.entity.equals(that.entity) &&
                this.id.equals(that.id) &&
                this.title.equals(that.title) &&
                this.location.equals(that.location) &&
                this.executableArgs.equals(that.executableArgs) &&
                this.inputParameters.equals(that.inputParameters);
    }

    @Override
    public int hashCode()
    {
        return this.entity.hashCode() + (43 * this.id.hashCode());
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() +
                "{entity=\"" + this.entity +
                "\", id=\"" + this.id +
                "\", title=\"" + this.title +
                ", location=" + this.location +
                ", executableArgs=" + this.executableArgs +
                ", inputParameters=" + this.inputParameters +
                "\"}";
    }

    @Override
    public TextLocation getLocation()
    {
        return this.location;
    }

    /**
     * Return the path of the entity the command applies to.
     *
     * @return entity path
     */
    public String getEntity()
    {
        return this.entity;
    }

    /**
     * Return the id of the command. This should be unique for the entity.
     *
     * @return command id
     */
    public String getId()
    {
        return this.id;
    }

    /**
     * Return the title of the command, which should be human understandable.
     *
     * @return command title
     */
    public String getTitle()
    {
        return this.title;
    }

    /**
     * Return the list of executable arguments of the command, which should be human understandable.
     *
     * @return command executableArgs
     */
    public Map<String, String> getExecutableArgs()
    {
        return this.executableArgs;
    }

    /**
     * Return the list of input parameters of the command, which should be human understandable.
     *
     * @return command inputParameters
     */
    public Map<String, LegendInputParamter> getInputParameters()
    {
        return this.inputParameters;
    }

    /**
     * Construct a new Legend command.
     *
     * @param entity   command entity path
     * @param id       command id
     * @param title    command title
     * @param location command location
     * @return new command
     */
    public static LegendCommand newCommand(String entity, String id, String title, TextLocation location)
    {
        return newCommand(entity, id, title, location, null);
    }

    /**
     * Construct a new Legend command.
     *
     * @param entity         command entity path
     * @param id             command id
     * @param title          command title
     * @param location       command location
     * @param executableArgs command executableArgs
     * @return new command
     */
    public static LegendCommand newCommand(String entity, String id, String title, TextLocation location, Map<String, String> executableArgs)
    {
        return newCommand(entity, id, title, location, executableArgs, null);
    }

    /**
     * Construct a new Legend command.
     *
     * @param entity         command entity path
     * @param id             command id
     * @param title          command title
     * @param location       command location
     * @param executableArgs command executableArgs
     * @param inputParameters command inputParameters
     * @return new command
     */
    public static LegendCommand newCommand(String entity, String id, String title, TextLocation location, Map<String, String> executableArgs, Map<String, LegendInputParamter> inputParameters)
    {
        return new LegendCommand(entity, id, title, location, executableArgs, inputParameters);
    }
}
