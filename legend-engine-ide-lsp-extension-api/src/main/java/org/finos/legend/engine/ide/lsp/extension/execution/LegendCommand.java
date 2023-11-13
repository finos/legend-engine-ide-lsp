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

import org.finos.legend.engine.ide.lsp.extension.text.Locatable;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * A Legend command which can be executed.
 */
public class LegendCommand implements Locatable
{
    private final String entity;
    private final String id;
    private final String title;
    private final TextInterval location;
    private final Map<String, String> executableArgs;

    private LegendCommand(String entity, String id, String title, TextInterval location)
    {
        this.entity = Objects.requireNonNull(entity, "entity is required");
        this.id = Objects.requireNonNull(id, "commandId is required");
        this.title = Objects.requireNonNull(title, "title is required");
        this.location = Objects.requireNonNull(location, "location is required");
        this.executableArgs = Collections.emptyMap();
    }

    private LegendCommand(String entity, String id, String title, TextInterval location, Map<String, String> executableArgs)
    {
        this.entity = Objects.requireNonNull(entity, "entity is required");
        this.id = Objects.requireNonNull(id, "commandId is required");
        this.title = Objects.requireNonNull(title, "title is required");
        this.location = Objects.requireNonNull(location, "location is required");
        this.executableArgs = Collections.unmodifiableMap(executableArgs);
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
                this.location.equals(that.location);
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
                "\"}";
    }

    @Override
    public TextInterval getLocation()
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
     * Construct a new Legend command.
     *
     * @param entity   command entity path
     * @param id       command id
     * @param title    command title
     * @param location command location
     * @return new command
     */
    public static LegendCommand newCommand(String entity, String id, String title, TextInterval location)
    {
        return new LegendCommand(entity, id, title, location);
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
    public static LegendCommand newCommand(String entity, String id, String title, TextInterval location, Map<String, String> executableArgs)
    {
        return new LegendCommand(entity, id, title, location, executableArgs);
    }

    public String getExecutableArg(String id)
    {
        return this.executableArgs.get(id);
    }
}
