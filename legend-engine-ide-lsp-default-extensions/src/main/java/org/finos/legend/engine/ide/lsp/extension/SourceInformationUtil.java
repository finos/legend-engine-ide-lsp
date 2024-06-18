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

import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.protocol.pure.v1.model.SourceInformation;

public final class SourceInformationUtil
{
    private SourceInformationUtil()
    {

    }

    /**
     * Transform a (valid) {@link SourceInformation} to a {@link TextInterval} location.
     *
     * @param sourceInfo source information
     * @return location
     */
    public static TextLocation toLocation(SourceInformation sourceInfo)
    {
        return TextLocation.newTextSource(sourceInfo.sourceId, sourceInfo.startLine - 1, sourceInfo.startColumn - 1, sourceInfo.endLine - 1, sourceInfo.endColumn - 1);
    }

    public static TextLocation toLocation(org.finos.legend.pure.m4.coreinstance.SourceInformation sourceInfo)
    {
        return TextLocation.newTextSource(sourceInfo.getSourceId(), sourceInfo.getStartLine() - 1, sourceInfo.getStartColumn() - 1, sourceInfo.getEndLine() - 1, sourceInfo.getEndColumn() - 1);
    }

    /**
     * Check if the source information is valid.
     *
     * @param sourceInfo source information
     * @return whether source information is valid
     */
    public static boolean isValidSourceInfo(SourceInformation sourceInfo)
    {
        return (sourceInfo != null) &&
                (sourceInfo != SourceInformation.getUnknownSourceInformation()) &&
                (sourceInfo.startLine > 0) &&
                (sourceInfo.startColumn > 0) &&
                (sourceInfo.startLine <= sourceInfo.endLine) &&
                ((sourceInfo.startLine == sourceInfo.endLine) ? (sourceInfo.startColumn <= sourceInfo.endColumn) : (sourceInfo.endColumn > 0));
    }
}
