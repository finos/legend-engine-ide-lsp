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

package org.finos.legend.engine.ide.lsp.server.integration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.DidOpenNotebookDocumentParams;
import org.eclipse.lsp4j.NotebookDocument;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.finos.legend.engine.ide.lsp.server.LegendLanguageService;
import org.finos.legend.engine.ide.lsp.server.request.LegendJsonToPureRequest;
import org.finos.legend.engine.ide.lsp.server.request.LegendWriteEntityRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
// all tests should finish but in case of some uncaught deadlock, timeout whole test
public class TestLegendLanguageServerSDLCIntegration
{
    @RegisterExtension
    static LegendLanguageServerIntegrationExtension extension = new LegendLanguageServerIntegrationExtension();

    @Test
    void jsonEntitiesToPureTextWorkspaceEdits() throws Exception
    {
        Path jsonFilePath = extension.addToWorkspace(
                "src/main/legend/LegalEntity.json",
                "{\n" +
                        "  \"content\": {\n" +
                        "    \"_type\": \"class\",\n" +
                        "    \"name\": \"A\",\n" +
                        "    \"package\": \"model\",\n" +
                        "    \"properties\": [\n" +
                        "      {\n" +
                        "        \"multiplicity\": {\n" +
                        "          \"lowerBound\": 1,\n" +
                        "          \"upperBound\": 1\n" +
                        "        },\n" +
                        "        \"name\": \"name\",\n" +
                        "        \"type\": \"String\"\n" +
                        "      }\n" +
                        "    ]\n" +
                        "  },\n" +
                        "  \"classifierPath\": \"meta::pure::metamodel::type::Class\"\n" +
                        "}\n"
        );

        // will be ignored as cannot be converted to text
        Path badJsonFilePath = extension.addToWorkspace(
                "src/main/legend/BadEntity.json",
                "{\n" +
                        "  \"content\": {\n" +
                        "    \"_type\": \"nonExistent\",\n" +
                        "    \"name\": \"A\",\n" +
                        "    \"package\": \"model\",\n" +
                        "    \"properties\": [\n" +
                        "      {\n" +
                        "        \"multiplicity\": {\n" +
                        "          \"lowerBound\": 1,\n" +
                        "          \"upperBound\": 1\n" +
                        "        },\n" +
                        "        \"name\": \"name\",\n" +
                        "        \"type\": \"String\"\n" +
                        "      }\n" +
                        "    ]\n" +
                        "  },\n" +
                        "  \"classifierPath\": \"meta::pure::metamodel::type::NonExistent\"\n" +
                        "}\n"
        );

        String pureFileUri = jsonFilePath.toUri().toString().replace("/src/main/legend/", "/src/main/pure/").replace(".json", ".pure");

        ApplyWorkspaceEditResponse editResponse = extension.futureGet(
                extension.getServer().getLegendLanguageService().jsonEntitiesToPureTextWorkspaceEdits(
                        new LegendJsonToPureRequest(List.of(jsonFilePath.toUri().toString(), badJsonFilePath.toUri().toString()))
                )
        );

        Assertions.assertTrue(editResponse.isApplied());

        // converts good file
        extension.clientLogged("logMessage - Info - Converting JSON protocol to Pure text for: " + jsonFilePath.toUri() + " -> " + pureFileUri);
        // ignore and log for bad file
        extension.clientLogged("logMessage - Error - Failed to convert JSON to pure for: " + badJsonFilePath.toUri());

        List<ApplyWorkspaceEditParams> workspaceEdits = extension.getClient().workspaceEdits;

        Assertions.assertEquals(1, workspaceEdits.size());

        WorkspaceEdit edit = workspaceEdits.get(0).getEdit();
        Assertions.assertEquals(0, edit.getChanges().size());

        List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = edit.getDocumentChanges();
        Assertions.assertEquals(2, documentChanges.size());

        Either<TextDocumentEdit, ResourceOperation> delete = documentChanges.get(0);
        Assertions.assertEquals(
                "RenameFile [\n" +
                        "  oldUri = \"" + jsonFilePath.toUri() + "\"\n" +
                        "  newUri = \"" + pureFileUri + "\"\n" +
                        "  options = null\n" +
                        "  kind = \"rename\"\n" +
                        "  annotationId = null\n" +
                        "]", delete.get().toString());

        Either<TextDocumentEdit, ResourceOperation> addText = documentChanges.get(1);
        Assertions.assertEquals(
                "TextDocumentEdit [\n" +
                        "  textDocument = VersionedTextDocumentIdentifier [\n" +
                        "    version = null\n" +
                        "    uri = \"" + pureFileUri + "\"\n" +
                        "  ]\n" +
                        "  edits = ArrayList (\n" +
                        "    TextEdit [\n" +
                        "      range = Range [\n" +
                        "        start = Position [\n" +
                        "          line = 0\n" +
                        "          character = 0\n" +
                        "        ]\n" +
                        "        end = Position [\n" +
                        "          line = 18\n" +
                        "          character = 0\n" +
                        "        ]\n" +
                        "      ]\n" +
                        "      newText = \"// Converted by Legend LSP from JSON file: src/main/legend/LegalEntity.json\\nClass model::A\\n{\\n  name: String[1];\\n}\\n\"\n" +
                        "    ]\n" +
                        "  )\n" +
                        "]",
                addText.get().toString());
    }

    @Test
    void oneEntityPerFileRefactoring() throws Exception
    {
        extension.getServer().getNotebookDocumentService().didOpen(new DidOpenNotebookDocumentParams(
                new NotebookDocument("_not_used_", "legend-book", 1, List.of()),
                List.of(new TextDocumentItem("vscode-notebook-cell://cell1.purebook", "legend", 1, "1"))
        ));

        // one file with one element and good name - no edits will happen
        extension.addToWorkspace("one/element.pure",
                "###Pure\n" +
                        "Class one::element\n" +
                        "{\n" +
                        "  a:Integer[1];\n" +
                        "}");

        // one file with one element and bad name - delete current, create/edit new one
        extension.addToWorkspace("one/element/wrongfile.pure",
                "###Pure\n" +
                        "Class another::one::element\n" +
                        "{\n" +
                        "  a:Integer[1];\n" +
                        "}");

        // one file with multiple elements - one element is correct - edit file, create/edit other files
        Path manyElementsOneElementCorrectPath = extension.addToWorkspace("many/elements.pure",
                "// This comment will be with element below\n" +
                        "###Pure\n" +
                        "function hello::world(): Integer[1]\n" +
                        "{\n" +
                        "  1 + 1;\n" +
                        "}\n" +
                        "###Relational\n" +
                        "// A comment here will be kept\n" +
                        "Database another::element()\n" +
                        "###Pure\n" +
                        "Class many::elements\n" +
                        "{\n" +
                        "  a:Integer[1];\n" +
                        "}\n"
        );

        // one file with multiple elements - create/edit new files, delete existing file
        extension.addToWorkspace("entities" + LegendLanguageService.PURE_FILE_DIRECTORY + "another/many/elements.pure",
                "###Relational\n" +
                        "// A comment here will be kept\n" +
                        "Database my::database()\n" +
                        "// This comment will be with element below\n" +
                        "###Pure\n" +
                        "function hello::moon(): Integer[1]\n" +
                        "{\n" +
                        "  1 + 1;\n" +
                        "}\n" +
                        "Class another::class\n" +
                        "{\n" +
                        "  a:Integer[1];\n" +
                        "}\n"
        );

        extension.futureGet(extension.getServer().getLegendLanguageService().oneEntityPerFileRefactoring());
        List<ApplyWorkspaceEditParams> workspaceEdits = extension.getClient().workspaceEdits;
        Assertions.assertEquals(1, workspaceEdits.size());

        WorkspaceEdit edit = workspaceEdits.get(0).getEdit();
        Assertions.assertEquals(0, edit.getChanges().size());

        List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = edit.getDocumentChanges();
        Assertions.assertEquals(15, documentChanges.size());
        String expected = "[\n" +
                // create - another::element file
                "  {\n" +
                "    \"right\": {\n" +
                "      \"uri\": \"__root_path__/another/element.pure\",\n" +
                "      \"options\": {\n" +
                "        \"ignoreIfExists\": true\n" +
                "      },\n" +
                "      \"kind\": \"create\"\n" +
                "    }\n" +
                "  },\n" +
                // edit - another::element file
                "  {\n" +
                "    \"left\": {\n" +
                "      \"textDocument\": {\n" +
                "        \"version\": null,\n" +
                "        \"uri\": \"__root_path__/another/element.pure\"\n" +
                "      },\n" +
                "      \"edits\": [\n" +
                "        {\n" +
                "          \"range\": {\n" +
                "            \"start\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            },\n" +
                "            \"end\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            }\n" +
                "          },\n" +
                "          \"newText\": \"###Relational\\n// A comment here will be kept\\nDatabase another::element()\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                // create - another::one::element file
                "  {\n" +
                "    \"right\": {\n" +
                "      \"uri\": \"__root_path__/another/one/element.pure\",\n" +
                "      \"options\": {\n" +
                "        \"ignoreIfExists\": true\n" +
                "      },\n" +
                "      \"kind\": \"create\"\n" +
                "    }\n" +
                "  },\n" +
                // edit - another::one::element file
                "  {\n" +
                "    \"left\": {\n" +
                "      \"textDocument\": {\n" +
                "        \"version\": null,\n" +
                "        \"uri\": \"__root_path__/another/one/element.pure\"\n" +
                "      },\n" +
                "      \"edits\": [\n" +
                "        {\n" +
                "          \"range\": {\n" +
                "            \"start\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            },\n" +
                "            \"end\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            }\n" +
                "          },\n" +
                "          \"newText\": \"Class another::one::element\\n{\\n  a:Integer[1];\\n}\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                // create - another::class file to add Pure code
                "  {\n" +
                "    \"right\": {\n" +
                "      \"uri\": \"__root_path__/entities/src/main/pure/another/class.pure\",\n" +
                "      \"options\": {\n" +
                "        \"ignoreIfExists\": true\n" +
                "      },\n" +
                "      \"kind\": \"create\"\n" +
                "    }\n" +
                "  },\n" +
                // edit - another::class file to add Pure code
                "  {\n" +
                "    \"left\": {\n" +
                "      \"textDocument\": {\n" +
                "        \"version\": null,\n" +
                "        \"uri\": \"__root_path__/entities/src/main/pure/another/class.pure\"\n" +
                "      },\n" +
                "      \"edits\": [\n" +
                "        {\n" +
                "          \"range\": {\n" +
                "            \"start\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            },\n" +
                "            \"end\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            }\n" +
                "          },\n" +
                "          \"newText\": \"Class another::class\\n{\\n  a:Integer[1];\\n}\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                // create - hello::moon file
                "  {\n" +
                "    \"right\": {\n" +
                "      \"uri\": \"__root_path__/entities/src/main/pure/hello/moon__Integer_1_.pure\",\n" +
                "      \"options\": {\n" +
                "        \"ignoreIfExists\": true\n" +
                "      },\n" +
                "      \"kind\": \"create\"\n" +
                "    }\n" +
                "  },\n" +
                // edit - hello::moon file
                "  {\n" +
                "    \"left\": {\n" +
                "      \"textDocument\": {\n" +
                "        \"version\": null,\n" +
                "        \"uri\": \"__root_path__/entities/src/main/pure/hello/moon__Integer_1_.pure\"\n" +
                "      },\n" +
                "      \"edits\": [\n" +
                "        {\n" +
                "          \"range\": {\n" +
                "            \"start\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            },\n" +
                "            \"end\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            }\n" +
                "          },\n" +
                "          \"newText\": \"// This comment will be with element below\\n\\nfunction hello::moon(): Integer[1]\\n{\\n  1 + 1;\\n}\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                // create - my::database file
                "  {\n" +
                "    \"right\": {\n" +
                "      \"uri\": \"__root_path__/entities/src/main/pure/my/database.pure\",\n" +
                "      \"options\": {\n" +
                "        \"ignoreIfExists\": true\n" +
                "      },\n" +
                "      \"kind\": \"create\"\n" +
                "    }\n" +
                "  },\n" +
                // edit - my::database file
                "  {\n" +
                "    \"left\": {\n" +
                "      \"textDocument\": {\n" +
                "        \"version\": null,\n" +
                "        \"uri\": \"__root_path__/entities/src/main/pure/my/database.pure\"\n" +
                "      },\n" +
                "      \"edits\": [\n" +
                "        {\n" +
                "          \"range\": {\n" +
                "            \"start\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            },\n" +
                "            \"end\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            }\n" +
                "          },\n" +
                "          \"newText\": \"###Relational\\n// A comment here will be kept\\nDatabase my::database()\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                // create - hello::world file
                "  {\n" +
                "    \"right\": {\n" +
                "      \"uri\": \"__root_path__/hello/world__Integer_1_.pure\",\n" +
                "      \"options\": {\n" +
                "        \"ignoreIfExists\": true\n" +
                "      },\n" +
                "      \"kind\": \"create\"\n" +
                "    }\n" +
                "  },\n" +
                // edit - hello::world file
                "  {\n" +
                "    \"left\": {\n" +
                "      \"textDocument\": {\n" +
                "        \"version\": null,\n" +
                "        \"uri\": \"__root_path__/hello/world__Integer_1_.pure\"\n" +
                "      },\n" +
                "      \"edits\": [\n" +
                "        {\n" +
                "          \"range\": {\n" +
                "            \"start\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            },\n" +
                "            \"end\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            }\n" +
                "          },\n" +
                "          \"newText\": \"// This comment will be with element below\\n\\nfunction hello::world(): Integer[1]\\n{\\n  1 + 1;\\n}\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                // edit - many::elements file (remove elements moved to their own files, but keep existing file)
                "  {\n" +
                "    \"left\": {\n" +
                "      \"textDocument\": {\n" +
                "        \"version\": 0,\n" +
                "        \"uri\": \"__root_path__/many/elements.pure\"\n" +
                "      },\n" +
                "      \"edits\": [\n" +
                "        {\n" +
                "          \"range\": {\n" +
                "            \"start\": {\n" +
                "              \"line\": 0,\n" +
                "              \"character\": 0\n" +
                "            },\n" +
                "            \"end\": {\n" +
                "              \"line\": 16,\n" +
                "              \"character\": 0\n" +
                "            }\n" +
                "          },\n" +
                "          \"newText\": \"Class many::elements\\n{\\n  a:Integer[1];\\n}\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                // delete - another/many/elements.pure
                "  {\n" +
                "    \"right\": {\n" +
                "      \"uri\": \"__root_path__/entities/src/main/pure/another/many/elements.pure\",\n" +
                "      \"kind\": \"delete\"\n" +
                "    }\n" +
                "  },\n" +
                // delete - one/element/wrongfile.pure
                "  {\n" +
                "    \"right\": {\n" +
                "      \"uri\": \"__root_path__/one/element/wrongfile.pure\",\n" +
                "      \"kind\": \"delete\"\n" +
                "    }\n" +
                "  }\n" +
                "]";
        Assertions.assertEquals(expected.replace("__root_path__/", extension.resolveWorkspacePath("").toUri().toString()),
                new GsonBuilder().setPrettyPrinting().create().toJson(documentChanges));
    }

    @Test
    void writeEntity() throws Exception
    {
        Path elementPath = extension.addToWorkspace("element.pure",
                "###Pure\n" +
                        "Class one::element\n" +
                        "{\n" +
                        "  a:Integer[1];\n" +
                        "}\n" +
                        "Class two::element\n" +
                        "{\n" +
                        "  a:Integer[1];\n" +
                        "}\n" +
                        "Class three::element\n" +
                        "{\n" +
                        "  a:Integer[1];\n" +
                        "}");

        Path mappingPath = extension.addToWorkspace("mapping.pure",
                "###Mapping\n" +
                        "// some comment here...\n" +
                        "Mapping vscodelsp::test::EmployeeMapping\n" +
                        "(\n" +
                        "   one::element : Pure\n" +
                        "   {\n" +
                        "      ~src two::element\n" +
                        "   }\n" +
                        ")");

        Path diagramtPath = extension.addToWorkspace("diagram.pure",
                "###Diagram\n" +
                        "Diagram showcase::northwind::model::NorthwindModelDiagram1\n" +
                        "{\n" +
                        "}"
        );

        String classEntityToWrite = "{\n" +
                "  \"_type\": \"class\",\n" +
                "  \"name\": \"element\",\n" +
                "  \"properties\": [\n" +
                "    {\n" +
                "      \"name\": \"a\",\n" +
                "      \"type\": \"Integer\",\n" +
                "      \"multiplicity\": {\n" +
                "        \"lowerBound\": 1,\n" +
                "        \"upperBound\": 1\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"b\",\n" +
                "      \"type\": \"String\",\n" +
                "      \"multiplicity\": {\n" +
                "        \"lowerBound\": 1,\n" +
                "        \"upperBound\": 1\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"package\": \"one\"\n" +
                "}";

        writeEntity("one::element", classEntityToWrite);

        String mappingToWrite = "{\n" +
                "  \"_type\": \"mapping\",\n" +
                "  \"name\": \"EmployeeMapping\",\n" +
                "  \"classMappings\": [\n" +
                "    {\n" +
                "      \"_type\": \"pureInstance\",\n" +
                "      \"root\": false,\n" +
                "      \"srcClass\": \"two::element\",\n" +
                "      \"propertyMappings\": [\n" +
                "        {\n" +
                "          \"_type\": \"purePropertyMapping\",\n" +
                "          \"property\": {\n" +
                "            \"property\": \"a\",\n" +
                "            \"class\": \"one::element\"\n" +
                "          },\n" +
                "          \"source\": \"\",\n" +
                "          \"transform\": {\n" +
                "            \"_type\": \"lambda\",\n" +
                "            \"body\": [\n" +
                "              {\n" +
                "                \"_type\": \"property\",\n" +
                "                \"property\": \"a\",\n" +
                "                \"parameters\": [\n" +
                "                  {\n" +
                "                    \"_type\": \"var\",\n" +
                "                    \"name\": \"src\"\n" +
                "                  }\n" +
                "                ]\n" +
                "              }\n" +
                "            ],\n" +
                "            \"parameters\": []\n" +
                "          },\n" +
                "          \"explodeProperty\": false\n" +
                "        }\n" +
                "      ],\n" +
                "      \"class\": \"one::element\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"package\": \"vscodelsp::test\"\n" +
                "}";

        writeEntity("vscodelsp::test::EmployeeMapping", mappingToWrite);

        String diagramEntityToWrite = "{\n" +
                "  \"_type\": \"diagram\",\n" +
                "  \"classViews\": [\n" +
                "    {\n" +
                "      \"class\": \"showcase::northwind::model::crm::Customer\",\n" +
                "      \"id\": \"7b39d77d-e490-4eca-9480-efff9078416d\",\n" +
                "      \"position\": {\n" +
                "        \"x\": 250,\n" +
                "        \"y\": 72\n" +
                "      },\n" +
                "      \"rectangle\": {\n" +
                "        \"height\": 156,\n" +
                "        \"width\": 236.98681640625\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"generalizationViews\": [],\n" +
                "  \"name\": \"NorthwindModelDiagram1\",\n" +
                "  \"package\": \"showcase::northwind::model\",\n" +
                "  \"propertyViews\": []\n" +
                "}";

        writeEntity("showcase::northwind::model::NorthwindModelDiagram1", diagramEntityToWrite);

        List<ApplyWorkspaceEditParams> workspaceEdits = extension.getClient().workspaceEdits;
        Assertions.assertEquals(3, workspaceEdits.size());

        Assertions.assertEquals("Edit element: one::element", workspaceEdits.get(0).getLabel());
        WorkspaceEdit oneElementEdit = workspaceEdits.get(0).getEdit();
        List<Either<TextDocumentEdit, ResourceOperation>> oneElementDocumentChanges = oneElementEdit.getDocumentChanges();
        Assertions.assertEquals(1, oneElementDocumentChanges.size());
        Assertions.assertEquals("{\n" +
                "  \"textDocument\": {\n" +
                "    \"version\": 0,\n" +
                "    \"uri\": \"" + elementPath.toUri() + "\"\n" +
                "  },\n" +
                "  \"edits\": [\n" +
                "    {\n" +
                "      \"range\": {\n" +
                "        \"start\": {\n" +
                "          \"line\": 1,\n" +
                "          \"character\": 0\n" +
                "        },\n" +
                "        \"end\": {\n" +
                "          \"line\": 4,\n" +
                "          \"character\": 1\n" +
                "        }\n" +
                "      },\n" +
                "      \"newText\": \"Class one::element\\n{\\n  a: Integer[1];\\n  b: String[1];\\n}\"\n" +
                "    }\n" +
                "  ]\n" +
                "}", getJson(oneElementDocumentChanges));

        Assertions.assertEquals("Edit element: vscodelsp::test::EmployeeMapping", workspaceEdits.get(1).getLabel());
        WorkspaceEdit mappingElementEdit = workspaceEdits.get(1).getEdit();
        List<Either<TextDocumentEdit, ResourceOperation>> mappingElementDocumentChanges = mappingElementEdit.getDocumentChanges();
        Assertions.assertEquals(1, mappingElementDocumentChanges.size());
        Assertions.assertEquals("{\n" +
                "  \"textDocument\": {\n" +
                "    \"version\": 0,\n" +
                "    \"uri\": \"" + mappingPath.toUri() + "\"\n" +
                "  },\n" +
                "  \"edits\": [\n" +
                "    {\n" +
                "      \"range\": {\n" +
                "        \"start\": {\n" +
                "          \"line\": 2,\n" +
                "          \"character\": 0\n" +
                "        },\n" +
                "        \"end\": {\n" +
                "          \"line\": 8,\n" +
                "          \"character\": 1\n" +
                "        }\n" +
                "      },\n" +
                "      \"newText\": \"Mapping vscodelsp::test::EmployeeMapping\\n(\\n  one::element: Pure\\n  {\\n    ~src two::element\\n    a: $src.a\\n  }\\n)\"\n" +
                "    }\n" +
                "  ]\n" +
                "}", getJson(mappingElementDocumentChanges));

        Assertions.assertEquals("Edit element: showcase::northwind::model::NorthwindModelDiagram1", workspaceEdits.get(2).getLabel());
        WorkspaceEdit diagramElementEdit = workspaceEdits.get(2).getEdit();
        List<Either<TextDocumentEdit, ResourceOperation>> diagramElementDocumentChanges = diagramElementEdit.getDocumentChanges();
        Assertions.assertEquals(1, diagramElementDocumentChanges.size());
        Assertions.assertEquals("{\n" +
                "  \"textDocument\": {\n" +
                "    \"version\": 0,\n" +
                "    \"uri\": \"" + diagramtPath.toUri() + "\"\n" +
                "  },\n" +
                "  \"edits\": [\n" +
                "    {\n" +
                "      \"range\": {\n" +
                "        \"start\": {\n" +
                "          \"line\": 1,\n" +
                "          \"character\": 0\n" +
                "        },\n" +
                "        \"end\": {\n" +
                "          \"line\": 3,\n" +
                "          \"character\": 1\n" +
                "        }\n" +
                "      },\n" +
                "      \"newText\": \"Diagram showcase::northwind::model::NorthwindModelDiagram1\\n{\\n  classView 7b39d77d-e490-4eca-9480-efff9078416d\\n  {\\n    class: showcase::northwind::model::crm::Customer;\\n    position: (250.0,72.0);\\n    rectangle: (236.98681640625,156.0);\\n  }\\n}\"\n" +
                "    }\n" +
                "  ]\n" +
                "}", getJson(diagramElementDocumentChanges));
    }

    private static String getJson(List<Either<TextDocumentEdit, ResourceOperation>> mappingElementDocumentChanges)
    {
        return new GsonBuilder().setPrettyPrinting().create().toJson(mappingElementDocumentChanges.get(0).getLeft());
    }

    private static void writeEntity(String entityPath, String classEntityToWrite) throws Exception
    {
        Map<String, ?> entityContent = new Gson().fromJson(classEntityToWrite, Map.class);
        LegendWriteEntityRequest request = new LegendWriteEntityRequest(entityPath, entityContent);
        extension.futureGet(extension.getServer().getLegendLanguageService().writeEntity(request));
    }
}
