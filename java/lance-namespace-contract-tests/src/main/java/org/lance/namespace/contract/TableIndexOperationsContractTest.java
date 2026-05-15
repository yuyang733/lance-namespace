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
import org.lance.namespace.model.CreateTableIndexRequest;
import org.lance.namespace.model.CreateTableRequest;
import org.lance.namespace.model.DescribeTableIndexStatsRequest;
import org.lance.namespace.model.DescribeTableIndexStatsResponse;
import org.lance.namespace.model.DropTableIndexRequest;
import org.lance.namespace.model.IndexContent;
import org.lance.namespace.model.ListTableIndicesRequest;
import org.lance.namespace.model.ListTableIndicesResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lance.namespace.contract.ContractAssertions.assertThrowsWithCode;

/**
 * Contract tests for table index operations:
 *
 * <ul>
 *   <li>{@code createTableIndex} (vector) and {@code createTableScalarIndex} (scalar)
 *   <li>{@code listTableIndices} - enumerate indices on a table
 *   <li>{@code describeTableIndexStats} - read stats about a specific index
 *   <li>{@code dropTableIndex} - delete an index by name
 * </ul>
 *
 * <p>Index lifecycle is asynchronous in many backends; tests assert only the metadata
 * round-tripped through the namespace, never the underlying physical index state.
 */
public abstract class TableIndexOperationsContractTest extends AbstractLanceNamespaceContractTest {

  protected List<String> ensureParentNamespace(String prefix) {
    if (supports(Capability.NAMESPACE_CREATE)) {
      String ns = uniqueNamespace(prefix);
      namespace.createNamespace(new CreateNamespaceRequest().id(id(ns)).mode("CREATE"));
      return id(ns);
    }
    return id();
  }

  /** Bootstrap a populated table that subsequent index tests can build indices over. */
  protected List<String> bootstrapTable(String prefix, int rowCount) {
    requires(Capability.DATA_CREATE_TABLE);

    List<String> parent = ensureParentNamespace(prefix);
    List<String> tid = child(parent, uniqueTable(prefix));
    namespace.createTable(
        new CreateTableRequest().id(tid).mode("CREATE"), buildArrowIpc(rowCount));
    return tid;
  }

  @Nested
  @DisplayName("createTableIndex (vector)")
  class CreateVectorIndex {

    @Test
    @DisplayName("creates an IVF_PQ index on the canonical 'vector' column")
    void createsVectorIndex() {
      requires(Capability.INDEX_CREATE_VECTOR);
      List<String> tid = bootstrapTable("idxv", 256);

      assertNotNull(
          namespace.createTableIndex(
              new CreateTableIndexRequest()
                  .id(tid)
                  .column("vector")
                  .indexType("IVF_PQ")
                  .distanceType("l2")
                  .name("vec_idx")));
    }

    @Test
    @DisplayName("on a missing table raises TABLE_NOT_FOUND")
    void missingTableRaisesNotFound() {
      requires(Capability.INDEX_CREATE_VECTOR);

      List<String> parent = ensureParentNamespace("idxvm");
      assertThrowsWithCode(
          "createTableIndex(missing table)",
          () ->
              namespace.createTableIndex(
                  new CreateTableIndexRequest()
                      .id(child(parent, uniqueTable("ghost")))
                      .column("vector")
                      .indexType("IVF_PQ")),
          ErrorCode.TABLE_NOT_FOUND);
    }

    @Test
    @DisplayName("on a non-existent column raises TABLE_COLUMN_NOT_FOUND or INVALID_INPUT")
    void unknownColumnRaises() {
      requires(Capability.INDEX_CREATE_VECTOR);
      List<String> tid = bootstrapTable("idxvc", 1);

      assertThrowsWithCode(
          "createTableIndex(unknown column)",
          () ->
              namespace.createTableIndex(
                  new CreateTableIndexRequest()
                      .id(tid)
                      .column("__nonexistent__")
                      .indexType("IVF_PQ")),
          ErrorCode.TABLE_COLUMN_NOT_FOUND,
          ErrorCode.INVALID_INPUT);
    }
  }

  @Nested
  @DisplayName("createTableScalarIndex")
  class CreateScalarIndex {

    @Test
    @DisplayName("creates a BTREE scalar index on the 'id' column")
    void createsScalarIndex() {
      requires(Capability.INDEX_CREATE_SCALAR);
      List<String> tid = bootstrapTable("idxs", 16);

      assertNotNull(
          namespace.createTableScalarIndex(
              new CreateTableIndexRequest()
                  .id(tid)
                  .column("id")
                  .indexType("BTREE")
                  .name("id_idx")));
    }
  }

  @Nested
  @DisplayName("listTableIndices")
  class ListIndices {

    @Test
    @DisplayName("returns the index just created")
    void listAfterCreate() {
      requires(Capability.INDEX_CREATE_VECTOR);
      requires(Capability.INDEX_LIST);

      List<String> tid = bootstrapTable("idxls", 256);
      String idxName = "vec_listed";
      namespace.createTableIndex(
          new CreateTableIndexRequest()
              .id(tid)
              .column("vector")
              .indexType("IVF_PQ")
              .name(idxName));

      ListTableIndicesResponse resp =
          namespace.listTableIndices(new ListTableIndicesRequest().id(tid));
      assertNotNull(resp);
      assertNotNull(resp.getIndexes(), "ListTableIndicesResponse.indexes must not be null");
      boolean found = false;
      for (IndexContent idx : resp.getIndexes()) {
        if (idxName.equals(idx.getIndexName())) {
          found = true;
          break;
        }
      }
      assertTrue(found, "listTableIndices must surface the index named " + idxName);
    }

    @Test
    @DisplayName("on a missing table raises TABLE_NOT_FOUND")
    void listOnMissingRaises() {
      requires(Capability.INDEX_LIST);

      List<String> parent = ensureParentNamespace("idxlsm");
      assertThrowsWithCode(
          "listTableIndices(missing)",
          () ->
              namespace.listTableIndices(
                  new ListTableIndicesRequest().id(child(parent, uniqueTable("ghost")))),
          ErrorCode.TABLE_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("describeTableIndexStats")
  class DescribeIndexStats {

    @Test
    @DisplayName("returns stats for a known index")
    void describeKnownIndex() {
      requires(Capability.INDEX_CREATE_VECTOR);
      requires(Capability.INDEX_STATS);

      List<String> tid = bootstrapTable("idxst", 256);
      String idxName = "vec_stats";
      namespace.createTableIndex(
          new CreateTableIndexRequest()
              .id(tid)
              .column("vector")
              .indexType("IVF_PQ")
              .name(idxName));

      DescribeTableIndexStatsResponse stats =
          namespace.describeTableIndexStats(
              new DescribeTableIndexStatsRequest().id(tid), idxName);
      assertNotNull(stats, "describeTableIndexStats must return non-null");
    }

    @Test
    @DisplayName("on an unknown index raises TABLE_INDEX_NOT_FOUND")
    void unknownIndexRaises() {
      requires(Capability.INDEX_STATS);

      List<String> tid = bootstrapTable("idxstm", 1);
      assertThrowsWithCode(
          "describeTableIndexStats(unknown)",
          () ->
              namespace.describeTableIndexStats(
                  new DescribeTableIndexStatsRequest().id(tid), "__nonexistent__"),
          ErrorCode.TABLE_INDEX_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("dropTableIndex")
  class DropIndex {

    @Test
    @DisplayName("removes a known index")
    void dropsIndex() {
      requires(Capability.INDEX_CREATE_VECTOR);
      requires(Capability.INDEX_DROP);

      List<String> tid = bootstrapTable("idxdrop", 256);
      String idxName = "vec_drop";
      namespace.createTableIndex(
          new CreateTableIndexRequest()
              .id(tid)
              .column("vector")
              .indexType("IVF_PQ")
              .name(idxName));

      assertNotNull(
          namespace.dropTableIndex(new DropTableIndexRequest().id(tid), idxName));
    }

    @Test
    @DisplayName("dropping an unknown index raises TABLE_INDEX_NOT_FOUND")
    void unknownIndexRaises() {
      requires(Capability.INDEX_DROP);

      List<String> tid = bootstrapTable("idxdropm", 1);
      assertThrowsWithCode(
          "dropTableIndex(unknown)",
          () ->
              namespace.dropTableIndex(
                  new DropTableIndexRequest().id(tid), "__nonexistent__"),
          ErrorCode.TABLE_INDEX_NOT_FOUND);
    }
  }
}
