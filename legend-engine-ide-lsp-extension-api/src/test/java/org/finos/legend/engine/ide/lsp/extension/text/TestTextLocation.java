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

package org.finos.legend.engine.ide.lsp.extension.text;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestTextLocation
{
    @Test
    public void testValidation()
    {
        NullPointerException e1 = Assertions.assertThrows(NullPointerException.class, () -> TextLocation.newTextSource(null, TextInterval.newInterval(0, 0, 0, 1)));
        Assertions.assertEquals("Document ID is required", e1.getMessage());

        NullPointerException e2 = Assertions.assertThrows(NullPointerException.class, () -> TextLocation.newTextSource("uri.pure", null));
        Assertions.assertEquals("Text interval is required", e2.getMessage());
    }

    @Test
    public void testSubsumes()
    {
        Assertions.assertTrue(TextLocation.newTextSource("uri.pure", TextInterval.newInterval(1, 10, 5, 17)).subsumes(
                TextLocation.newTextSource("uri.pure", 1, 10, 5, 17)));

        Assertions.assertFalse(TextLocation.newTextSource("uri.pure", TextInterval.newInterval(1, 10, 5, 17)).subsumes(
                TextLocation.newTextSource("other.pure", 1, 10, 5, 17)));
    }

    @Test
    public void testToString()
    {
        Assertions.assertEquals("TextSource{documentId='hello.pure', textInterval=0:0-75:0}", TextLocation.newTextSource("hello.pure", 0, 0, 75, 0).toString());
    }
}
