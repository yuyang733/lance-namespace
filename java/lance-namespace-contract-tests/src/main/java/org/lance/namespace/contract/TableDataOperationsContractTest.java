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
import org.lance.namespace.model.CountTableRowsRequest;
import org.lance.namespace.model.CreateNamespaceRequest;
import org.lance.namespace.model.CreateTableRequest;
import org.lance.namespace.model.CreateTableResponse;
import org.lance.namespace.model.DeleteFromTableRequest;
import org.lance.namespace.model.DeleteFromTableResponse;
import org.lance.namespace.model.DescribeTableRequest;
import org.lance.namespace.model.DescribeTableResponse;
import org.lance.namespace.model.InsertIntoTableRequest;
import org.lance.namespace.model.InsertIntoTableResponse;
import org.lance.namespace.model.QueryTableRequest;
import org.lance.namespace.model.QueryTableRequestVector;
import org.lance.namespace.model.UpdateTableRequest;
import org.lance.namespace.model.UpdateTableResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lance.namespace.contract.ContractAssertions.assertThrowsWithCode;
import static org.lance.namespace.contract.ContractAssertions.assertValidVersion;
import static org.lance.namespace.contract.ContractAssertions.assertVersionMonotonic;

/**
 * Contract tests for the table data-plane operations:
 *
 * <ul>
 *   <li>{@code createTable} - create a new table populated with Arrow IPC data
 *   <li>{@code insertIntoTable} - append/overwrite rows
 *   <li>{@code updateTable / deleteFromTable} - SQL-style mutation
 *   <li>{@code queryTable} - vector / FTS / SQL query
 *   <li>{@code countTableRows} - row counting with optional predicate / version
 * </ul>
 *
 * <p>All operations are exercised against the canonical test schema {@code (id INT32, name UTF8,
 * vector FLOAT32[4])} provided by the abstract base class.
 */
public abstract class TableDataOperationsContractTest extends AbstractLanceNamespaceContractTest {

  protected List<String> ensureParentNamespace(String prefix) {
    if (supports(Capability.NAMESPACE_CREATE)) {
      String ns = uniqueNamespace(prefix);
      namespace.createNamespace(new CreateNamespaceRequest().id(id(ns)).mode("CREATE"));
      return id(ns);
    }
    return id();
  }

  protected List<String> tableId(List<String> parent, String tableName) {
    return child(parent, tableName);
  }

  /**
   * Resolve the current version of a table via {@code describeTable}. Returns {@code null} if the
   * implementation does not surface a version on {@link DescribeTableResponse}.
   */
  protected Long currentVersion(List<String> tid) {
    DescribeTableResponse resp =
        namespace.describeTable(new DescribeTableRequest().id(tid).loadDetailedMetadata(true));
    return resp == null ? null : resp.getVersion();
  }

  /**
   * Bootstrap a fresh table populated with {@code rowCount} rows of canonical-schema data and
   * return its identifier.
   */
  protected List<String> bootstrapTableWithData(String prefix, int rowCount) {
    requires(Capability.DATA_CREATE_TABLE);

    List<String> parent = ensureParentNamespace(prefix);
    List<String> tid = tableId(parent, uniqueTable(prefix));

    CreateTableResponse resp =
        namespace.createTable(
            new CreateTableRequest().id(tid).mode("CREATE"), buildArrowIpc(rowCount));
    assertNotNull(resp, "createTable must return a non-null response");
    assertValidVersion("createTable", resp.getVersion());
    return tid;
  }

  @Nested
  @DisplayName("createTable")
  class CreateTable {

    @Test
    @DisplayName("creates a table from Arrow IPC; response carries version >= 1")
    void createsTable() {
      List<String> tid = bootstrapTableWithData("ct", 10);
      assertNotNull(tid);
    }

    @Test
    @DisplayName("with mode=CREATE on existing table raises TABLE_ALREADY_EXISTS")
    void rejectsDuplicateCreate() {
      requires(Capability.DATA_CREATE_TABLE);

      List<String> parent = ensureParentNamespace("ctd");
      List<String> tid = tableId(parent, uniqueTable("ctd"));
      namespace.createTable(
          new CreateTableRequest().id(tid).mode("CREATE"), buildArrowIpc(1));

      assertThrowsWithCode(
          "createTable(duplicate)",
          () ->
              namespace.createTable(
                  new CreateTableRequest().id(tid).mode("CREATE"), buildArrowIpc(1)),
          ErrorCode.TABLE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("rejects malformed Arrow IPC payload with INVALID_INPUT")
    void rejectsMalformedIpc() {
      requires(Capability.DATA_CREATE_TABLE);

      List<String> parent = ensureParentNamespace("ctbad");
      List<String> tid = tableId(parent, uniqueTable("ctbad"));

      assertThrowsWithCode(
          "createTable(bad ipc)",
          () ->
              namespace.createTable(
                  new CreateTableRequest().id(tid).mode("CREATE"),
                  new byte[] {0x00, 0x01, 0x02}),
          ErrorCode.INVALID_INPUT);
    }
  }

  @Nested
  @DisplayName("insertIntoTable")
  class InsertIntoTable {

    @Test
    @DisplayName("append mode adds rows; row count and version both increase")
    void appendIncreasesRowCount() {
      requires(Capability.DATA_INSERT);
      requires(Capability.DATA_COUNT_ROWS);

      List<String> tid = bootstrapTableWithData("ins", 5);
      Long beforeVersion = currentVersion(tid);

      InsertIntoTableResponse first =
          namespace.insertIntoTable(
              new InsertIntoTableRequest().id(tid).mode("append"), buildArrowIpc(3));
      assertNotNull(first, "insertIntoTable must return a non-null response");

      Long count1 = namespace.countTableRows(new CountTableRowsRequest().id(tid));
      assertNotNull(count1);
      assertEquals(8L, count1.longValue(), "after appending 3 rows the count must be 8");

      InsertIntoTableResponse second =
          namespace.insertIntoTable(
              new InsertIntoTableRequest().id(tid).mode("append"), buildArrowIpc(2));
      assertNotNull(second);

      Long count2 = namespace.countTableRows(new CountTableRowsRequest().id(tid));
      assertNotNull(count2);
      assertEquals(10L, count2.longValue(), "after appending 2 more rows the count must be 10");

      Long afterVersion = currentVersion(tid);
      if (beforeVersion != null && afterVersion != null) {
        assertVersionMonotonic(beforeVersion, afterVersion);
      }
    }

    @Test
    @DisplayName("on a missing table raises TABLE_NOT_FOUND")
    void missingRaisesNotFound() {
      requires(Capability.DATA_INSERT);

      List<String> parent = ensureParentNamespace("insmiss");
      assertThrowsWithCode(
          "insertIntoTable(missing)",
          () ->
              namespace.insertIntoTable(
                  new InsertIntoTableRequest()
                      .id(tableId(parent, uniqueTable("missing")))
                      .mode("append"),
                  buildArrowIpc(1)),
          ErrorCode.TABLE_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("updateTable / deleteFromTable")
  class UpdateAndDelete {

    @Test
    @DisplayName("updateTable applies an assignment; response carries new version")
    void updatesTable() {
      requires(Capability.DATA_UPDATE);
      List<String> tid = bootstrapTableWithData("upd", 5);

      List<List<String>> updates = new ArrayList<>();
      updates.add(Arrays.asList("name", "'updated'"));

      UpdateTableResponse resp =
          namespace.updateTable(
              new UpdateTableRequest().id(tid).predicate("id >= 0").updates(updates));
      assertValidVersion("updateTable", resp.getVersion());
    }

    @Test
    @DisplayName("updateTable with empty 'updates' list raises INVALID_INPUT")
    void updateRejectsEmptyAssignments() {
      requires(Capability.DATA_UPDATE);
      List<String> tid = bootstrapTableWithData("updbad", 1);

      assertThrowsWithCode(
          "updateTable(empty updates)",
          () ->
              namespace.updateTable(
                  new UpdateTableRequest().id(tid).updates(Collections.emptyList())),
          ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("deleteFromTable removes matching rows and returns new version")
    void deletesRows() {
      requires(Capability.DATA_DELETE);
      requires(Capability.DATA_COUNT_ROWS);

      List<String> tid = bootstrapTableWithData("del", 10);

      DeleteFromTableResponse resp =
          namespace.deleteFromTable(new DeleteFromTableRequest().id(tid).predicate("id < 5"));
      assertValidVersion("deleteFromTable", resp.getVersion());

      Long count = namespace.countTableRows(new CountTableRowsRequest().id(tid));
      assertNotNull(count, "countTableRows must return a non-null Long");
      assertTrue(
          count <= 5L, "After deleting id<5 from 10 rows, count must be <= 5, got " + count);
    }
  }

  @Nested
  @DisplayName("queryTable")
  class QueryTable {

    @Test
    @DisplayName("vector query with k=N returns Arrow IPC stream")
    void vectorQueryReturnsIpc() {
      requires(Capability.DATA_QUERY);
      List<String> tid = bootstrapTableWithData("qv", 20);

      QueryTableRequestVector vec = new QueryTableRequestVector();
      vec.setSingleVector(Arrays.asList(0.0f, 0.1f, 0.2f, 0.3f));

      byte[] result =
          namespace.queryTable(
              new QueryTableRequest()
                  .id(tid)
                  .vector(vec)
                  .vectorColumn("vector")
                  .k(5)
                  .distanceType("l2"));
      assertNotNull(result, "queryTable must return a non-null Arrow IPC byte array");
      assertTrue(result.length > 0, "queryTable IPC stream must not be empty");
    }

    @Test
    @DisplayName("k must be a positive integer (k <= 0 raises INVALID_INPUT)")
    void rejectsNonPositiveK() {
      requires(Capability.DATA_QUERY);
      List<String> tid = bootstrapTableWithData("qvbad", 1);

      QueryTableRequestVector vec = new QueryTableRequestVector();
      vec.setSingleVector(Arrays.asList(0f, 0f, 0f, 0f));

      assertThrowsWithCode(
          "queryTable(k=0)",
          () ->
              namespace.queryTable(
                  new QueryTableRequest().id(tid).vector(vec).vectorColumn("vector").k(0)),
          ErrorCode.INVALID_INPUT);
    }
  }

  @Nested
  @DisplayName("countTableRows")
  class CountTableRows {

    @Test
    @DisplayName("returns the exact row count with no predicate")
    void countsAllRows() {
      requires(Capability.DATA_COUNT_ROWS);
      List<String> tid = bootstrapTableWithData("cnt", 7);

      Long count = namespace.countTableRows(new CountTableRowsRequest().id(tid));
      assertNotNull(count);
      assertEquals(7L, count.longValue(), "countTableRows must return exact row count");
    }

    @Test
    @DisplayName("with predicate returns only rows matching the predicate")
    void countsWithPredicate() {
      requires(Capability.DATA_COUNT_ROWS);
      List<String> tid = bootstrapTableWithData("cntp", 10);

      Long count =
          namespace.countTableRows(new CountTableRowsRequest().id(tid).predicate("id < 3"));
      assertNotNull(count);
      assertEquals(3L, count.longValue(), "countTableRows(id<3) on 10 rows must return 3");
    }
  }
}
