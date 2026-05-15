/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lance.namespace.contract;

import org.lance.namespace.errors.ErrorCode;
import org.lance.namespace.model.CreateNamespaceRequest;
import org.lance.namespace.model.DeclareTableRequest;
import org.lance.namespace.model.DescribeTableRequest;
import org.lance.namespace.model.DescribeTableResponse;
import org.lance.namespace.model.DropTableRequest;
import org.lance.namespace.model.ListTablesRequest;
import org.lance.namespace.model.ListTablesResponse;
import org.lance.namespace.model.RenameTableRequest;
import org.lance.namespace.model.TableExistsRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lance.namespace.contract.ContractAssertions.assertThrowsWithCode;
import static org.lance.namespace.contract.ContractAssertions.isPageTokenTerminal;

/**
 * Contract tests for the metadata-only table operations:
 *
 * <ul>
 *   <li>{@code declareTable} - register a table identifier without writing data
 *   <li>{@code listTables} - paginated enumeration
 *   <li>{@code describeTable} - read-back of table metadata
 *   <li>{@code tableExists} - existence probe
 *   <li>{@code dropTable} - removal
 *   <li>{@code renameTable} - rename within the same parent namespace
 * </ul>
 *
 * <p>These operations exercise the metadata plane only - no Arrow IPC payload is required.
 */
public abstract class TableMetadataOperationsContractTest
    extends AbstractLanceNamespaceContractTest {

  /** Create a parent namespace if the backend supports it; otherwise return root id. */
  protected List<String> ensureParentNamespace(String prefix) {
    if (supports(Capability.NAMESPACE_CREATE)) {
      String ns = uniqueNamespace(prefix);
      namespace.createNamespace(new CreateNamespaceRequest().id(id(ns)).mode("CREATE"));
      return id(ns);
    }
    return id();
  }

  /** Build a table identifier inside {@code parent}. */
  protected List<String> tableId(List<String> parent, String tableName) {
    return child(parent, tableName);
  }

  @Nested
  @DisplayName("declareTable")
  class DeclareTable {

    @Test
    @DisplayName("declares a brand-new table identifier")
    void declaresFreshTable() {
      requires(Capability.TABLE_DECLARE);

      List<String> parent = ensureParentNamespace("decl");
      List<String> tid = tableId(parent, uniqueTable("decl"));
      assertNotNull(namespace.declareTable(new DeclareTableRequest().id(tid)));
    }

    @Test
    @DisplayName("declaring an existing table raises TABLE_ALREADY_EXISTS")
    void declareDuplicateRaises() {
      requires(Capability.TABLE_DECLARE);

      List<String> parent = ensureParentNamespace("decldup");
      List<String> tid = tableId(parent, uniqueTable("decldup"));
      namespace.declareTable(new DeclareTableRequest().id(tid));

      assertThrowsWithCode(
          "declareTable(duplicate)",
          () -> namespace.declareTable(new DeclareTableRequest().id(tid)),
          ErrorCode.TABLE_ALREADY_EXISTS);
    }
  }

  @Nested
  @DisplayName("describeTable")
  class DescribeTable {

    @Test
    @DisplayName("on a missing table raises TABLE_NOT_FOUND")
    void describeMissing() {
      requires(Capability.TABLE_DESCRIBE);

      List<String> parent = ensureParentNamespace("descmiss");
      List<String> tid = tableId(parent, uniqueTable("ghost"));

      assertThrowsWithCode(
          "describeTable(missing)",
          () -> namespace.describeTable(new DescribeTableRequest().id(tid)),
          ErrorCode.TABLE_NOT_FOUND);
    }

    @Test
    @DisplayName("on a declared table returns a non-null response")
    void describeDeclared() {
      requires(Capability.TABLE_DECLARE);
      requires(Capability.TABLE_DESCRIBE);

      List<String> parent = ensureParentNamespace("desc");
      List<String> tid = tableId(parent, uniqueTable("desc"));
      namespace.declareTable(new DeclareTableRequest().id(tid));

      DescribeTableResponse resp =
          namespace.describeTable(new DescribeTableRequest().id(tid));
      assertNotNull(resp, "describeTable response must not be null");
    }
  }

  @Nested
  @DisplayName("tableExists")
  class TableExists {

    @Test
    @DisplayName("succeeds for declared table; raises TABLE_NOT_FOUND otherwise")
    void existsMatrix() {
      requires(Capability.TABLE_DECLARE);
      requires(Capability.TABLE_EXISTS);

      List<String> parent = ensureParentNamespace("exists");
      List<String> present = tableId(parent, uniqueTable("p"));
      namespace.declareTable(new DeclareTableRequest().id(present));

      assertDoesNotThrow(
          () -> namespace.tableExists(new TableExistsRequest().id(present)),
          "tableExists must succeed for a declared table");

      List<String> missing = tableId(parent, uniqueTable("m"));
      assertThrowsWithCode(
          "tableExists(missing)",
          () -> namespace.tableExists(new TableExistsRequest().id(missing)),
          ErrorCode.TABLE_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("listTables")
  class ListTables {

    @Test
    @DisplayName("includes declared tables; pagination terminates")
    void listIncludesDeclared() {
      requires(Capability.TABLE_DECLARE);
      requires(Capability.TABLE_LIST);

      List<String> parent = ensureParentNamespace("ls");
      String name = uniqueTable("ls");
      namespace.declareTable(new DeclareTableRequest().id(tableId(parent, name)));

      String pageToken = null;
      boolean found = false;
      int safety = 100;
      do {
        ListTablesRequest req = new ListTablesRequest().id(parent);
        if (pageToken != null) {
          req.setPageToken(pageToken);
        }
        ListTablesResponse resp = namespace.listTables(req);
        assertNotNull(resp);
        Set<String> tables = resp.getTables();
        assertNotNull(tables, "ListTablesResponse.tables must always be populated");
        if (tables.contains(name)) {
          found = true;
        }
        pageToken = resp.getPageToken();
        safety--;
      } while (!isPageTokenTerminal(pageToken) && safety > 0);

      assertTrue(safety > 0, "listTables pagination must terminate");
      assertTrue(found, "listTables must include the declared table " + name);
    }

    @Test
    @DisplayName("respects the 'limit' parameter when supplied")
    void respectsLimit() {
      requires(Capability.TABLE_DECLARE);
      requires(Capability.TABLE_LIST);

      List<String> parent = ensureParentNamespace("lim");
      for (int i = 0; i < 3; i++) {
        namespace.declareTable(
            new DeclareTableRequest().id(tableId(parent, uniqueTable("lim" + i))));
      }

      ListTablesResponse resp =
          namespace.listTables(new ListTablesRequest().id(parent).limit(2));
      assertNotNull(resp);
      assertNotNull(resp.getTables());
      assertTrue(
          resp.getTables().size() <= 2,
          "listTables(limit=2) must return at most 2 tables, got " + resp.getTables().size());
    }
  }

  @Nested
  @DisplayName("dropTable")
  class DropTable {

    @Test
    @DisplayName("removes a declared table; subsequent describe raises TABLE_NOT_FOUND")
    void dropAndDescribe() {
      requires(Capability.TABLE_DECLARE);
      requires(Capability.TABLE_DROP);
      requires(Capability.TABLE_DESCRIBE);

      List<String> parent = ensureParentNamespace("drop");
      List<String> tid = tableId(parent, uniqueTable("drop"));
      namespace.declareTable(new DeclareTableRequest().id(tid));

      assertNotNull(namespace.dropTable(new DropTableRequest().id(tid)));
      assertThrowsWithCode(
          "describeTable(after drop)",
          () -> namespace.describeTable(new DescribeTableRequest().id(tid)),
          ErrorCode.TABLE_NOT_FOUND);
    }

    @Test
    @DisplayName("on a missing table raises TABLE_NOT_FOUND")
    void dropMissing() {
      requires(Capability.TABLE_DROP);

      List<String> parent = ensureParentNamespace("dropmiss");
      List<String> ghost = tableId(parent, uniqueTable("ghost"));
      assertThrowsWithCode(
          "dropTable(missing)",
          () -> namespace.dropTable(new DropTableRequest().id(ghost)),
          ErrorCode.TABLE_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("renameTable")
  class RenameTable {

    @Test
    @DisplayName("renames a declared table to a new name within the same parent")
    void renamesTable() {
      requires(Capability.TABLE_DECLARE);
      requires(Capability.TABLE_RENAME);
      requires(Capability.TABLE_EXISTS);

      List<String> parent = ensureParentNamespace("rn");
      String oldName = uniqueTable("old");
      String newName = uniqueTable("new");
      List<String> oldId = tableId(parent, oldName);
      namespace.declareTable(new DeclareTableRequest().id(oldId));

      assertNotNull(
          namespace.renameTable(new RenameTableRequest().id(oldId).newTableName(newName)));

      assertDoesNotThrow(
          () ->
              namespace.tableExists(
                  new TableExistsRequest().id(tableId(parent, newName))),
          "renamed table must exist under its new name");
      assertThrowsWithCode(
          "tableExists(old name)",
          () -> namespace.tableExists(new TableExistsRequest().id(oldId)),
          ErrorCode.TABLE_NOT_FOUND);
    }

    @Test
    @DisplayName("rename of a missing table raises TABLE_NOT_FOUND")
    void renameMissing() {
      requires(Capability.TABLE_RENAME);

      List<String> parent = ensureParentNamespace("rnmiss");
      List<String> ghost = tableId(parent, uniqueTable("ghost"));
      assertThrowsWithCode(
          "renameTable(missing)",
          () ->
              namespace.renameTable(
                  new RenameTableRequest().id(ghost).newTableName("anything")),
          ErrorCode.TABLE_NOT_FOUND);
    }
  }
}
