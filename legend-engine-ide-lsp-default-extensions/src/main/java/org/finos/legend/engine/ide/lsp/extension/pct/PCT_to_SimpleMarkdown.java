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

package org.finos.legend.engine.ide.lsp.extension.pct;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.multimap.list.ListMultimap;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.pure.m3.pct.aggregate.generation.DocumentationGeneration;
import org.finos.legend.pure.m3.pct.aggregate.model.Documentation;
import org.finos.legend.pure.m3.pct.aggregate.model.FunctionDocumentation;
import org.finos.legend.pure.m3.pct.functions.model.FunctionDefinition;
import org.finos.legend.pure.m3.pct.functions.model.Signature;
import org.finos.legend.pure.m3.pct.reports.model.AdapterKey;
import org.finos.legend.pure.m3.pct.reports.model.FunctionTestResults;
import org.finos.legend.pure.m3.pct.reports.model.TestInfo;
import org.finos.legend.shared.stuctures.TreeNode;

import java.util.ArrayList;
import java.util.Stack;

public class PCT_to_SimpleMarkdown
{
    static MutableMap<String, String> moduleURLs = Maps.mutable.empty();

    public static String buildCompatibilityMD()
    {
        moduleURLs.put("grammar", "https://github.com/finos/legend-pure/tree/master/legend-pure-core/legend-pure-m3-core/src/main/resources");
        moduleURLs.put("essential", "https://github.com/finos/legend-pure/tree/master/legend-pure-core/legend-pure-m3-core/src/main/resources");
        moduleURLs.put("standard", "https://github.com/finos/legend-engine/tree/master/legend-engine-core/legend-engine-core-pure/legend-engine-pure-code-functions-base/legend-engine-pure-functions-standard/src/main/resources/");
        moduleURLs.put("relation", "https://github.com/finos/legend-engine/tree/master/legend-engine-core/legend-engine-core-pure/legend-engine-pure-code-functions-relation/legend-engine-pure-functions-relation-pure/src/main/resources");

        Documentation doc = DocumentationGeneration.buildDocumentation();

        ListMultimap<String, AdapterKey> grouped = Lists.mutable.withAll(doc.adapters).groupBy(x -> x.adapter.group);
        MutableList<AdapterKey> orderedAdapters = grouped.keysView().toSortedList().flatCollect(x -> grouped.get(x).toSortedListBy(z -> z.adapter.name));
        orderedAdapters.add(orderedAdapters.remove(0));

        // Organize by source
        MutableListMultimap<String, FunctionDocumentation> ordered = Lists.mutable.withAll(doc.functionsDocumentation).select(x -> x.functionDefinition.name != null)
                .groupBy(x ->
                {
                    String id = x.functionDefinition.sourceId;
                    return id.substring(x.reportScope.filePath.length(), id.lastIndexOf("/"));
                });

        // Build Tree
        TreeNode root = new TreeNode("root");
        ordered.keyMultiValuePairsView()
                .toSortedListBy(Pair::getOne)
                .forEach(x ->
                        {
                            TreeNode node = root;
                            for (String z : x.getOne().split("/"))
                            {
                                node = node.createOrReturnChild(z);
                            }

                            for (FunctionDocumentation d : x.getTwo().toSortedListBy(v -> v.functionDefinition.name))
                            {
                                MutableList<String> row = Lists.mutable.empty();
                                row.add("<div style='color:#AAAAAA'>" + d.reportScope.module + "</div>");
                                row.add(printFuncName(d));
                                if (!d.functionDefinition.signatures.isEmpty() && d.functionDefinition.signatures.get(0).platformOnly)
                                {
                                    row.add("          <div style='color:#00C72B' class='hover-text'>" + d.functionDefinition.testCount + "<div class='tooltip-text' id='top'>Executed outside of PCT</div></div>");
                                    for (int i = 0; i < orderedAdapters.size() - 1; i++)
                                    {
                                        // Should not have any tests...
                                        row.add("<div style='color:#AAAAAA'>-</div>");
                                    }
                                }
                                else
                                {
                                    row.addAll(orderedAdapters.collect(a -> writeTest(d, a)));
                                }
                                node.addChild(new TreeNode(row));
                            }
                        }
                );
        String compatibility = "<div id=\"content\"><div id=\"compatibility\"><BR/><BR/>\n" +
                "    <table id=\"myTable\" style=\"border-spacing:0px;width:900;text-align: center\">\n" +
                "        <tr>\n" +
                "           <th colspan='2' style='width:100'></th>\n" +
                addGroups(orderedAdapters) +
                "        </tr>\n" +
                "        <tr>\n" +
                "           <th style='width:100'></th>\n" +
                "           <th style='width:10'>Group</th>\n" +
                "           <th style='width:200'>Function</th>\n" +
                addHeaders(orderedAdapters) + "\n" +
                "        </tr>\n" +
                root.getChildren().collectWithIndex((n, i) -> addTableRow(n, "", String.valueOf(i), orderedAdapters)).makeString("\n") +
                "\n    </table>\n<BR/><BR/><BR/>" +
                bottomFinos +
                "</div></div>\n";

        return getTop("Compatibility") + compatibility + bottom;
    }

    public static String buildDocumentationMD()
    {
        {
            Documentation doc = DocumentationGeneration.buildDocumentation();

            // Organize by source
            MutableListMultimap<String, FunctionDocumentation> ordered = Lists.mutable.withAll(doc.functionsDocumentation).select(x -> x.functionDefinition.name != null)
                    .groupBy(x ->
                    {
                        String id = x.functionDefinition.sourceId;
                        return id.substring(x.reportScope.filePath.length(), id.lastIndexOf("/"));
                    });

            StringBuilder documentation = new StringBuilder("<div id=\"content\"><div style ='padding:100px;' id=\"documentation\">");

            ordered.keyMultiValuePairsView()
                    .toSortedListBy(Pair::getOne)
                    .forEach(x ->
                            {
                                for (FunctionDocumentation d : x.getTwo().toSortedListBy(v -> v.functionDefinition.name))
                                {
                                    documentation.append(addSignatures(d));
                                }
                            }
                    );
            documentation.append(bottomFinos);
            documentation.append("</div></div>");

            return getTop("Documentation") + documentation + bottom;
        }
    }

    private static String addTableRow(TreeNode node, String tab, String id, MutableList<AdapterKey> adapters)
    {
        if (node.getValue() instanceof String)
        {
            String pos = tab.isEmpty() ? "top" : "bottom";
            String borderLine = "border-" + pos + "-style:solid!important;border-" + pos + "-width:1px!important;" + ("border-" + pos + "-color:#ffa4c4;");
            String emptyCell = "<td style='" + borderLine + "'>&nbsp;</td>";
            return "<tr id ='" + id + "'>" +
                    "<td style='" + (tab.isEmpty() ? borderLine : "") + "color: #ffa4c4;text-align:left!important'>" + tab + "<a onclick=\"flip('" + id + "', [" + node.getChildren().collectWithIndex((n, i) -> printAllChildrenIds(n, id + "_" + node.getValue(), i)).makeString(", ") + "])\">" + node.getValue() + "</a></td>" +
                    emptyCell +
                    emptyCell +
                    adapters.collect(c -> emptyCell).makeString("") +
                    "</tr>" +
                    node.getChildren().collectWithIndex((n, i) -> addTableRow(n, tab + "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;", id + "_" + node.getValue() + "_" + i, adapters)).makeString("\n");
        }
        else if (node.getValue() instanceof MutableList)
        {
            MutableList<String> vals = (MutableList<String>) node.getValue();
            return "<tr id ='" + id + "'><td>&nbsp;</td>" + vals.collect(v -> "<td>" + v + "</td>").makeString("") + "</tr>";
        }
        else
        {
            throw new RuntimeException(node.getValue().getClass() + " is not supported!");
        }
    }

    private static String printAllChildrenIds(TreeNode node, String parentId, int index)
    {
        String id = parentId + "_" + index;
        return "'" + id + "', " + node.getChildren().collectWithIndex((c, i) -> printAllChildrenIds(c, id + "_" + node.getValue(), i)).makeString(", ");
    }


    private static String writeTest(FunctionDocumentation z, AdapterKey a)
    {
        FunctionTestResults results = z.functionTestResults.get(a);
        if (results != null)
        {
            MutableList<TestInfo> tests = Lists.mutable.withAll(results.tests);
            int success = tests.select(t -> t.success).size();
            String color = success == 0 ? "#C70039" : success != tests.size() ? "#FFA500" : "#00C72B";
            return "          <div style='color:" + color + "' class='hover-text'>" + success + "/" + tests.size() + "<div class='tooltip-text' id='top'>" + testDetail(tests) + "</div></div>";
        }
        else
        {
            return "          <div style='color:#AAAAAA'>&empty;</div>";
        }
    }

    private static String testDetail(MutableList<TestInfo> tests)
    {
        return tests.sortThisBy(x -> x.testName).collect(x -> "<span style='color:" + (x.success ? "00FF00" : "#FF0000") + "'>" + x.testName + "</span><span>" + (x.errorMessage == null ? "" : x.errorMessage) + "</span>").makeString("<BR>");

    }

    private static String printFuncName(FunctionDocumentation functionDocumentation)
    {
        FunctionDefinition f = functionDocumentation.functionDefinition;
        String color = isPlatformOnly(f) ? "color:#000000;" : f.name == null ? "color:#79d6db" : "color:#DDDDDD;";
        String character = getGrammarCharacter(f);
        return (character != null ? "<span style='color:#34eb92'>" + character + "&nbsp;&nbsp;</span>" : "") + "<a ' style='" + color + "'>" + (f.name == null ? "composition-tests" : f.name) + "</a>";

    }

    private static String addGroups(MutableList<AdapterKey> adapterKeys)
    {
        ListMultimap<String, AdapterKey> grouped = adapterKeys.groupBy(x -> x.adapter.group);
        return grouped.keysView().toSortedList().collect(x -> "<th colspan='" + grouped.get(x).size() + "'>" + x + "</th>").makeString("");
    }

    private static String addHeaders(MutableList<AdapterKey> adapterKeys)
    {
        return adapterKeys.collect(c -> c.adapter.name).collect(a -> "          <TH style='width:10'>" + a + "</TH>").makeString("\n");
    }

    private static String addSignatures(FunctionDocumentation functionDocumentation)
    {
        String module = functionDocumentation.reportScope.module;

        FunctionDefinition f = functionDocumentation.functionDefinition;
        // Add function name
        String func = f.sourceId.split("/")[f.sourceId.split("/").length - 1];
        String funcName = func.substring(0, func.length() - 5);
        String funcHeaderString = "<div id=\"" + f.sourceId + "\" class=\"entireFunction\"> <h2 class=\"anchor\" id=\"" + f.sourceId + "Heading\">" + funcName + // Function name
                "<a href=\"" + moduleURLs.get(module) + f.sourceId + "\" target=\"_blank\" rel=\"noopener noreferrer\"><img class=\"github-icon\" src=\" https://cdn-icons-png.flaticon.com/512/25/25231.png\"alt=\"See examples\"></a>" +
                "</h2>";


        // Add all docs, signature details, and usage
        StringBuilder funcDetailsString = new StringBuilder("<ul class=\"function\" style=\"text-align:left\">\n");

        // Add docs
        funcDetailsString.append(getDoc(f).isEmpty() ? "" : "<div class=\"functionDoc\">" + getDoc(f) + "</div>");

        if (f.signatures != null && !f.signatures.isEmpty())
        {
            for (int i = 0; i < f.signatures.size(); i++)
            {
                String rawFunction = f.signatures.get(i).simple.split("::")[f.signatures.get(i).simple.split("::").length - 1];

                funcDetailsString.append("<div class=\"oneFunction\">");
                ArrayList<ArrayList<String>> paramPairs = parseParams(rawFunction);

                String returns = rawFunction.split(":")[rawFunction.split(":").length - 1];
                returns = returns.replaceAll("<", "&lt");
                returns = returns.replaceAll(">", "&gt");

                // Build whole signature
                funcDetailsString.append("<div class=\"functionSignature\"><a><span class=\"functionName\">")
                        .append(funcName)
                        .append("</span>(");
                for (int j = 0; j < paramPairs.get(0).size(); j++)
                {
                    funcDetailsString.append("<span>")
                            .append(paramPairs.get(0).get(j))
                            .append("</span><span class=\"functionParamMultiplicity\">")
                            .append(paramPairs.get(1).get(j))
                            .append("</span>, ");
                }
                // Remove last comma
                if (!paramPairs.get(0).isEmpty())
                {
                    funcDetailsString = funcDetailsString.replace(funcDetailsString.length() - 2, funcDetailsString.length(), "");
                }

                int lastBracketIdx = returns.lastIndexOf("[");
                funcDetailsString.append("): \n" + "<span>")
                        .append(returns, 0, lastBracketIdx)
                        .append("</span><span class=\"functionReturnMultiplicity\">")
                        .append(returns.substring(lastBracketIdx))
                        .append("</span></a></div>");
                // Build params list
                funcDetailsString.append("<div class=\"functionDetails\"><li><span class=\"parametersLabel\">Parameters</span></li>");
                for (int j = 0; j < paramPairs.get(0).size(); j++)
                {
                    funcDetailsString.append("<li class=\"functionParams\"><span class=\"functionParamType\">")
                            .append(paramPairs.get(0).get(j))
                            .append("</span><span class=\"functionParamMultiplicity\">")
                            .append(paramPairs.get(1).get(j))
                            .append("</span></li>\n");
                }

                // Build returns
                lastBracketIdx = returns.lastIndexOf("[");
                funcDetailsString.append("<li><span class=\"returnsLabel\">Returns</span></li>")
                        .append("<li class=\"functionReturns\"><span class=\"functionReturnType\">")
                        .append(returns, 0, lastBracketIdx)
                        .append("</span><span class=\"functionReturnMultiplicity\">")
                        .append(returns.substring(lastBracketIdx))
                        .append("</span></li>");

                funcDetailsString.append(getUsage(f, i).isEmpty() ? "" :
                        "<li><span class=\"usageLabel\">Usage</span></li>" +
                                "<div class=\"functionUsage\"><span class=\"codeBlockContainer\">" +
                                "<code>" + getUsage(f, i) + "</code></span></div>");

                funcDetailsString.append("</div></div>");

            }
            return funcHeaderString + funcDetailsString + "</ul></div>";
        }
        else
        {
            return funcHeaderString + "</div>";
        }
    }

    public static ArrayList<ArrayList<String>> parseParams(String functionSignature)
    {
        ArrayList<String> paramTypes = new ArrayList<>();
        ArrayList<String> paramMults = new ArrayList<>();
        ArrayList<ArrayList<String>> paramPairs = new ArrayList<>();
        String paramsString = functionSignature.substring(functionSignature.indexOf("(") + 1, functionSignature.lastIndexOf(")"));
        Stack<Character> stack = new Stack<>();

        int start = 0;

        for (int i = 0; i < paramsString.length(); i++)
        {
            // Add onto stack
            if (paramsString.charAt(i) == '<')
            {
                stack.push(paramsString.charAt(i));
            }
            // Remove from stack
            if (paramsString.charAt(i) == '>' && paramsString.charAt(i - 1) != '-') // Check to make sure it's not a lambda
            {
                stack.pop();
            }
            // Parse params
            if (stack.isEmpty() && paramsString.charAt(i) == ']')
            {
                String param = paramsString.substring(start, i + 1);
                paramTypes.add(param.substring(0, param.lastIndexOf('[')));
                paramMults.add(param.substring(param.lastIndexOf('[')));
                // Go next if not end of params
                if (i != paramsString.length() - 1)
                {
                    start = i + 3; // Need to account for ", " in between params
                }
            }
        }
        paramPairs.add(paramTypes);
        paramPairs.add(paramMults);
        return paramPairs;
    }

    public static String getDoc(FunctionDefinition def)
    {
        return (ListIterate.detect(def.signatures, x -> x.documentation != null) == null) ? ""
                : ListIterate.detect(def.signatures, x -> x.documentation != null).documentation;
    }

    public static String getUsage(FunctionDefinition def, int sigIndex)
    {
        return "";
//        Signature signature = def.signatures.get(sigIndex) == null ? null : def.signatures.get(sigIndex);
//        return (signature != null ? signature.usage : null) == null ? "" : signature.usage.replaceAll("\\\\'", "'");
    }


    public static boolean isPlatformOnly(FunctionDefinition def)
    {
        return ListIterate.detect(def.signatures, x -> x.platformOnly) != null;
    }

    public static String getGrammarCharacter(FunctionDefinition def)
    {
        Signature signature = ListIterate.detect(def.signatures, x -> x.grammarCharacter != null);
        return signature == null ? null : signature.grammarCharacter;
    }

    private static String getTop(String page)
    {
        String navbar;
        String style;
        String search;
        switch (page)
        {
            case "Compatibility":
                navbar = navbarCompatibility;
                style = styleCompatibility;
                search = searchCompatibility;
                break;
            case "Documentation":
                navbar = navbarDocumentation;
                style = styleDocumentation;
                search = searchDocumentation;
                break;
            default:
                navbar = "";
                style = "";
                search = "";
                break;
        }

        return
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                        "<style>\n" +
                        "* {box-sizing: border-box;}\n" +
                        "body {\n" +
                        "  margin: 0;\n" +
                        "  background-color: #302c2c;\n" +
                        "  display: flex;\n" +
                        "  flex-direction: column;\n" +
                        "  font-family: \"Roboto\", condensed;\n" +
                        "  height: 100%;\n" +
                        "}\n" +
                        ".header {\n" +
                        "  display: flex;\n" +
                        "  flex-direction: column;\n" +
                        "  background-color: #302c2c;\n" +
                        "  z-index: 2;\n" +
                        "  width: 100%;\n" +
                        "  position: sticky;\n" +
                        "  align-self: flex-start;\n" +
                        "  top: 0;\n" +
                        "  overflow-y: auto;\n" +
                        "}\n" +
                        ".topnav {\n" +
                        "  overflow: hidden;\n" +
                        "  background-color: #e9e9e9;\n" +
                        "}\n" +
                        "th {\n" +
                        "  text-align: center;\n" +
                        "}\n" +
                        ".topnav a {\n" +
                        "  float: left;\n" +
                        "  display: block;\n" +
                        "  color: black;\n" +
                        "  text-align: center;\n" +
                        "  padding: 14px 16px;\n" +
                        "  text-decoration: none;\n" +
                        "  font-size: 17px;\n" +
                        "}\n" +
                        ".legend {\n" +
                        "  width: 300px;\n" +
                        "}\n" +
                        ".github-icon {\n" +
                        "width: 20px;\n" +
                        "height: 20px;\n" +
                        "display: inline;\n" +
                        "margin-left: 7px;\n" +
                        "}" +
                        ".topnav a:hover {\n" +
                        "  background-color: #ddd;\n" +
                        "  color: black;\n" +
                        "}\n" +
                        ".topnav a.active {\n" +
                        "  background-color: #ffa4c4;\n" +
                        "  color: white;\n" +
                        "}\n" +
                        ".topnav input[type=text] {\n" +
                        "  float: right;\n" +
                        "  padding: 6px;\n" +
                        "  margin-top: 8px;\n" +
                        "  margin-right: 16px;\n" +
                        "  border: none;\n" +
                        "  font-size: 17px;\n" +
                        "}\n" +
                        "  a {\n" +
                        "    color: #ffa4c4;  \n" +
                        "  }\n" +
                        "  p {\n" +
                        "    color: #ffa4c4;  \n" +
                        "  }\n" +
                        "@media screen and (max-width: 600px) {\n" +
                        "  .topnav a, .topnav input[type=text] {\n" +
                        "    float: none;\n" +
                        "    display: block;\n" +
                        "    text-align: left;\n" +
                        "    width: 100%;\n" +
                        "    margin: 0;\n" +
                        "    padding: 14px;\n" +
                        "  }\n" +
                        "  .topnav input[type=text] {\n" +
                        "    color: #ffa4c4;  \n" +
                        "    border: 1px solid #ccc;  \n" +

                        "  }\n" +
                        "}\n" +
                        style +
                        search +
                        navbar;
    }

    private static final String searchDocumentation = "<script>\n" +
            "function searchFuncDocumentation() {\n" +
            "    var input, filter, entireFunctions, specificFunctions, funcName, func, i, j, txtValue, showHeader;\n" +
            "    input = document.getElementById(\"myInput\");\n" +
            "    filter = input.value.toUpperCase();\n" +
            "    entireFunctions = document.getElementsByClassName(\"entireFunction\");\n" +
            "    for (i = 0; i < entireFunctions.length; i++) {\n" +
            "        showHeader = false;\n" +
            "        specificFunctions = entireFunctions[i].getElementsByClassName(\"oneFunction\");\n" +
            "        for (j = 0; j < specificFunctions.length; j++) {\n" +
            "            func = specificFunctions[j];\n" +
            "            funcName = func.getElementsByTagName(\"a\")[0];\n" +
            "            txtValue = funcName.textContent || func.innerText;\n" +
            "            if (txtValue.toUpperCase().indexOf(filter) > -1) {\n" +
            "                func.style.display = \"\";\n" +
            "            } else {\n" +
            "                func.style.display = \"none\";\n" +
            "            }\n" +
            "            showHeader = showHeader || txtValue.toUpperCase().indexOf(filter) > -1;\n" +
            "        }\n" +
            "        entireFunctions[i].style.display = showHeader ? \"\" : \"none\";\n" +
            "    }\n" +
            "}\n" +
            "</script>\n";

    private static final String searchCompatibility = "<script>\n" +
            "function searchFuncCompatibility() {\n" +
            "  var input, filter, table, tr, td, i, txtValue;\n" +
            "  input = document.getElementById(\"myInput\");\n" +
            "  filter = input.value.toUpperCase();\n" +
            "  table = document.getElementById(\"myTable\");\n" +
            "  tr = table.getElementsByTagName(\"tr\");\n" +
            "  for (i = 0; i < tr.length; i++) {\n" +
            "    td = tr[i].getElementsByTagName(\"a\")[0];\n" +
            "    if (td) {\n" +
            "      txtValue = td.textContent || td.innerText;\n" +
            "      if (txtValue.toUpperCase().indexOf(filter) > -1) {\n" +
            "        tr[i].style.display = \"\";\n" +
            "      } else {\n" +
            "        tr[i].style.display = \"none\";\n" +
            "      }\n" +
            "    }       \n" +
            "  }\n" +
            "}\n" +
            "</script>\n";

    private static final String navbarCompatibility = "<div class=\"header\">\n\n" +
            "<img src=\"https://legend.finos.org/img/legend.svg\" class=\"legend\">\n\n" +
            "<div class=\"topnav\" id=\"Menu\">\n" +
            "  <a class=\"active\" id=\"CompatabilityButton\">Compatability</a>\n\n" +
            "  <a id=\"DocumentationButton\">" + "<span>[Documentation](./PCT_Report_Documentation.md)</span>" + "</a>\n" +
            "<input type=\"text\" id=\"myInput\" onkeyup=\"searchFuncCompatibility()\" placeholder=\"Search\" title=\"Type in a function\">\n" +
            "</div></div>\n";

    private static final String navbarDocumentation = "<div class=\"header\">" +
            "<img src=\"https://legend.finos.org/img/legend.svg\" class=\"legend\">\n\n" +
            "<div class=\"topnav\" id=\"Menu\">\n\n" +
            "  <a id=\"CompatabilityButton\">" + "<span>[Compatibility](./PCT_Report_Compatibility.md)</span>" + "</a>\n" +
            "  <a class=\"active\" id=\"DocumentationButton\">Documentation</a>\n" +
            "<input type=\"text\" id=\"myInput\" onkeyup=\"searchFuncDocumentation()\" placeholder=\"Search\" title=\"Type in a function\">\n" +
            "</div></div>\n";

    private static final String styleCompatibility =
            "            body {\n" +
                    "               font-family: \"Roboto\" condensed;\n" +
                    "               color: #ffa4c4;\n" +
                    "            }\n" +
                    "           #content {\n" +
                    "               display: flex;\n" +
                    "               flex-direction: row;\n" +
                    "               justify-content: center;\n" +
                    "               height: 85%;\n" +
                    "            }" +
                    "            td {\n" +
                    "               text-align: center;\n" +
                    "            }\n" +
                    "            .tooltip-text {\n" +
                    "               visibility: hidden;\n" +
                    "               position: absolute;\n" +
                    "               z-index: 1;\n" +
                    "               color: white;\n" +
                    "               font-size: 12px;\n" +
                    "               background-color: #192733;\n" +
                    "               border-radius: 10px;\n" +
                    "               padding: 10px 15px 10px 15px;\n" +
                    "            }" +
                    "\n" +
                    "            .hover-text:hover .tooltip-text {\n" +
                    "               visibility: visible;\n" +
                    "            }" +
                    "\n" +
                    "            .hover-text {\n" +
                    "               position: relative;\n" +
                    "               display: inline-block;\n" +
                    "               font-family: \"Roboto\" condensed;\n" +
                    "               text-align: center;\n" +
                    "            }\n" +
                    "            #compatibility {\n" +
                    "               display: flex;\n" +
                    "               flex-direction: column;\n" +
                    "               height: 100%;\n" +
                    "               width: 100%;\n" +
                    "               align-items: center;\n" +
                    "               scrollbar-color: #252525 #3f3838;\n" +
                    "            }" +
                    "\n" +

                    "        </style>\n" +
                    "        <script>\n" +
                    "           function flip(stateId, ids)\n" +
                    "           {\n" +
                    "               var state = document.getElementById(stateId).data_openState;\n" +
                    "               state = state === undefined ? true : state;\n" +
                    "               ids.forEach((ele)=> flipOne(!state, ele));\n" +
                    "               document.getElementById(stateId).data_openState = !state;\n" +
                    "           }\n" +
                    "           function flipOne(open, id)\n" +
                    "           {\n" +
                    "               document.getElementById(id).style = open ? 'visibility:true' : 'visibility:collapse';\n" +
                    "           }\n" +
                    "        </script>\n";

    private static final String styleDocumentation =
            "            body {\n" +
                    "               font-family: \"Roboto\" condensed;\n" +
                    "               color: #ffa4c4;\n" +
                    "           }\n" +
                    "           #content {\n" +
                    "               display: flex;\n" +
                    "               flex-direction: row;\n" +
                    "               justify-content: center;\n" +
                    "               height: 85%;\n" +
                    "            }" +
                    "           #documentation {\n" +
                    "               display: flex;\n" +
                    "               flex-direction: column;\n" +
                    "               overflow-y: auto;\n" +
                    "               height: 100%;\n" +
                    "               width: 60%;\n" +
                    "               scrollbar-color: #252525 #3f3838;\n" +
                    "            }" +
                    "\n" +
                    "           .entireFunction {\n" +
                    "               display: flex;\n" +
                    "               flex-direction: column;\n" +
                    "           }" +
                    "\n" +
                    "          .function {\n" +
                    "               padding: 1rem 0 2rem;\n" +
                    "               width: 100%;\n" +
                    "           }" +
                    "\n" +
                    "        .functionDoc {\n" +
                    "               color: white;\n" +
                    "               padding: .5rem 1rem;\n" +
                    "           }" +
                    "\n" +
                    "        .functionSignature {\n" +
                    "               background-color: #4d3b3b;\n" +
                    "               padding: .5rem 1rem;\n" +
                    "           }" +
                    "\n" +
                    "        .functionName {\n" +
                    "               color: #5c77ec;\n" +
                    "           }" +
                    "\n" +
                    "        .functionDetails {\n" +
                    "               margin-top: 1rem;\n" +
                    "               margin-bottom: 2rem;\n" +
                    "           }" +
                    "\n" +
                    "        .functionParams, .functionReturns {\n" +
                    "               margin-right: .5rem;\n" +
                    "               margin-left: 2rem;\n" +
                    "               padding-top: .25rem;\n" +
                    "               padding-bottom: .25rem;\n" +
                    "           }" +
                    "\n" +
                    "        .functionParamType, .functionReturnType {\n" +
                    "           }" +
                    "\n" +
                    "        .functionParamMultiplicity, .functionReturnMultiplicity {\n" +
                    "               color: white;\n" +
                    "           }" +
                    "\n" +
                    "        .parametersLabel, .returnsLabel, .usageLabel {\n" +
                    "               background-color: #252525;\n" +
                    "               display: block;\n" +
                    "               margin: 1rem 0 .5rem;\n" +
                    "               padding: .5rem 1rem;\n" +
                    "               width: fit-content\n" +
                    "           }" +
                    "\n" +
                    "        .functionUsage {\n" +
                    "               background-color: #292d3e;\n" +
                    "               margin-left: 2rem;\n" +
                    "               margin-right: .5rem;\n" +
                    "               padding: 1rem 1rem;\n" +
                    "               scrollbar-width: thin;\n" +
                    "           }" +
                    "\n" +
                    "        .codeBlockContainer {\n" +
                    "           }" +
                    "\n" +
                    "            h2 {\n" +
                    "                display: block;\n" +
                    "                margin-block-start: 0.83em;\n" +
                    "                margin-block-end: 0.83em;\n" +
                    "                margin-inline-start: 0px;\n" +
                    "                margin-inline-end: 0px;\n" +
                    "                unicode-bidi: isolate;\n" +
                    "                text-align: left;\n" +
                    "            }" +
                    "            .sourceLink {\n" +
                    "                padding: 0 .5rem;\n" +
                    "                text-align: left;\n" +
                    "                color: inherit;\n" +
                    "            }" +
                    "\n" +
                    "            li {\n" +
                    "                list-style-type:none;\n" +
                    "            }" +
                    "\n" +
                    "            code {\n" +
                    "                color: white;\n" +
                    "                list-style-type:none;\n" +
                    "            }" +
                    "\n" +
                    "</style>\n" +
                    "<script>\n" +
                    "</script>\n";



    private static final String bottomFinos = "<div style=\"text-align: center;\">\n" +
            "<img src=\"https://www.finos.org/hubfs/FINOS/finos-logo/FINOS_Icon_Wordmark_Name_horz_White.svg\"alt=\"Legend\"style=\"width:300;height:300;\">\n" +
            "</div>";
    private static final String bottom = "</body>\n" + "</html>";
}
