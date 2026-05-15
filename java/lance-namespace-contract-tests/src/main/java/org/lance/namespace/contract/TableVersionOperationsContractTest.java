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
import org.lance.namespace.model.BatchCreateTableVersionsRequest;
import org.lance.namespace.model.BatchCreateTableVersionsResponse;
import org.lance.namespace.model.BatchDeleteTableVersionsRequest;
import org.lance.namespace.model.CreateNamespaceRequest;
import org.lance.namespace.model.CreateTableVersionEntry;
import org.lance.namespace.model.CreateTableVersionRequest;
import org.lance.namespace.model.CreateTableVersionResponse;
import org.lance.namespace.model.DeclareTableRequest;
import org.lance.namespace.model.DescribeTableVersionRequest;
import org.lance.namespace.model.DescribeTableVersionResponse;
import org.lance.namespace.model.ListTableVersionsRequest;
import org.lance.namespace.model.ListTableVersionsResponse;
import org.lance.namespace.model.TableVersion;
import org.lance.namespace.model.VersionRange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lance.namespace.contract.ContractAssertions.assertThrowsWithCode;

/**
 * Contract tests for the table version manifest plane:
 *
 * <ul>
 *   <li>{@code createTableVersion} - put-if-not-exists registration of a single version
 *   <li>{@code listTableVersions} - paginated enumeration
 *   <li>{@code describeTableVersion} - lookup by version number
 *   <li>{@code batchCreateTableVersions} - atomic multi-table commit
 *   <li>{@code batchDeleteTableVersions} - bulk metadata cleanup over ranges
 * </ul>
 *
 * <p>Per the spec these operations only manage version <em>metadata</em>; they do not touch
 * physical data files. Backends that delegate version tracking to an external store (e.g. a
 * relational catalog) are the primary audience.
 */
public abstract class TableVersionOperationsContractTest
    extends AbstractLanceNamespaceContractTest {

  protected List<String> ensureParentNamespace(String prefix) {
    if (supports(Capability.NAMESPACE_CREATE)) {
      String ns = uniqueNamespace(prefix);
      namespace.createNamespace(new CreateNamespaceRequest().id(id(ns)).mode("CREATE"));
      return id(ns);
    }
    return id();
  }

  protected List<String> declareTable(String prefix) {
    requires(Capability.TABLE_DECLARE);
    List<String> parent = ensureParentNamespace(prefix);
    List<String> tid = child(parent, uniqueTable(prefix));
    namespace.declareTable(new DeclareTableRequest().id(tid));
    return tid;
  }

  /** Build a minimal {@link CreateTableVersionRequest} with the required fields populated. */
  protected CreateTableVersionRequest versionRequest(List<String> tid, long version) {
    return new CreateTableVersionRequest()
        .id(tid)
        .version(version)
        .manifestPath("/test/manifests/v" + version + ".manifest");
  }

  @Nested
  @DisplayName("createTableVersion")
  class CreateVersion {

    @Test
    @DisplayName("creates a fresh version entry; response carries the new version")
    void createsVersion() {
      requires(Capability.VERSION_CREATE);
      List<String> tid = declareTable("vc");

      CreateTableVersionResponse resp =
          namespace.createTableVersion(versionRequest(tid, 1L));
      assertNotNull(resp);
      assertNotNull(resp.getVersion(), "createTableVersion response must include a version");
      assertEquals(
          1L,
          resp.getVersion().getVersion().longValue(),
          "createTableVersion must echo the supplied version number");
    }

    @Test
    @DisplayName("creating a duplicate version raises CONCURRENT_MODIFICATION")
    void duplicateVersionRaises() {
      requires(Capability.VERSION_CREATE);
      List<String> tid = declareTable("vcd");

      namespace.createTableVersion(versionRequest(tid, 1L));
      assertThrowsWithCode(
          "createTableVersion(duplicate)",
          () -> namespace.createTableVersion(versionRequest(tid, 1L)),
          ErrorCode.CONCURRENT_MODIFICATION);
    }
  }

  @Nested
  @DisplayName("describeTableVersion")
  class DescribeVersion {

    @Test
    @DisplayName("returns the manifest path for a known version")
    void describesKnown() {
      requires(Capability.VERSION_CREATE);
      requires(Capability.VERSION_DESCRIBE);
      List<String> tid = declareTable("vd");

      namespace.createTableVersion(versionRequest(tid, 7L));
      DescribeTableVersionResponse resp =
          namespace.describeTableVersion(
              new DescribeTableVersionRequest().id(tid).version(7L));
      assertNotNull(resp);
      TableVersion v = resp.getVersion();
      assertNotNull(v, "describeTableVersion response.version must not be null");
      assertEquals(7L, v.getVersion().longValue());
      assertNotNull(v.getManifestPath(), "TableVersion.manifestPath is required by spec");
    }

    @Test
    @DisplayName("on an unknown version raises TABLE_VERSION_NOT_FOUND")
    void unknownVersionRaises() {
      requires(Capability.VERSION_DESCRIBE);
      List<String> tid = declareTable("vdm");

      assertThrowsWithCode(
          "describeTableVersion(missing)",
          () ->
              namespace.describeTableVersion(
                  new DescribeTableVersionRequest().id(tid).version(99999L)),
          ErrorCode.TABLE_VERSION_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("listTableVersions")
  class ListVersions {

    @Test
    @DisplayName("includes versions previously created; pagination terminates")
    void listsCreatedVersions() {
      requires(Capability.VERSION_CREATE);
      requires(Capability.VERSION_LIST);
      List<String> tid = declareTable("vl");
      namespace.createTableVersion(versionRequest(tid, 1L));
      namespace.createTableVersion(versionRequest(tid, 2L));
      namespace.createTableVersion(versionRequest(tid, 3L));

      String pageToken = null;
      int safety = 100;
      int totalSeen = 0;
      boolean sawV2 = false;
      do {
        ListTableVersionsRequest req = new ListTableVersionsRequest().id(tid);
        if (pageToken != null) {
          req.setPageToken(pageToken);
        }
        ListTableVersionsResponse resp = namespace.listTableVersions(req);
        assertNotNull(resp);
        assertNotNull(resp.getVersions(), "ListTableVersionsResponse.versions must not be null");
        for (TableVersion v : resp.getVersions()) {
          totalSeen++;
          if (v.getVersion() != null && v.getVersion() == 2L) {
            sawV2 = true;
          }
        }
        pageToken = resp.getPageToken();
        safety--;
      } while (!ContractAssertions.isPageTokenTerminal(pageToken) && safety > 0);

      assertTrue(safety > 0, "listTableVersions pagination must terminate");
      assertTrue(totalSeen >= 3, "listTableVersions must surface all created versions");
      assertTrue(sawV2, "listTableVersions must include version 2 in the result set");
    }
  }

  @Nested
  @DisplayName("batchCreateTableVersions")
  class BatchCreate {

    @Test
    @DisplayName("atomically creates entries across multiple tables")
    void atomicBatchCreate() {
      requires(Capability.VERSION_BATCH_CREATE);
      requires(Capability.VERSION_DESCRIBE);

      List<String> a = declareTable("vba");
      List<String> b = declareTable("vbb");

      CreateTableVersionEntry e1 =
          new CreateTableVersionEntry().id(a).version(1L).manifestPath("/a/v1");
      CreateTableVersionEntry e2 =
          new CreateTableVersionEntry().id(b).version(1L).manifestPath("/b/v1");

      BatchCreateTableVersionsResponse resp =
          namespace.batchCreateTableVersions(
              new BatchCreateTableVersionsRequest().entries(Arrays.asList(e1, e2)));
      assertNotNull(resp);
      assertNotNull(resp.getVersions());
      assertEquals(2, resp.getVersions().size(), "every entry in a batch must produce a result");

      // Both must be visible after the batch
      assertNotNull(
          namespace.describeTableVersion(
              new DescribeTableVersionRequest().id(a).version(1L)));
      assertNotNull(
          namespace.describeTableVersion(
              new DescribeTableVersionRequest().id(b).version(1L)));
    }

    @Test
    @DisplayName("a single conflict aborts the entire batch (atomic)")
    void batchIsAtomic() {
      requires(Capability.VERSION_BATCH_CREATE);
      requires(Capability.VERSION_CREATE);
      requires(Capability.VERSION_DESCRIBE);

      List<String> a = declareTable("vbat1");
      List<String> b = declareTable("vbat2");

      // Pre-create version 1 on table b -> the batch must fail
      namespace.createTableVersion(versionRequest(b, 1L));

      CreateTableVersionEntry e1 =
          new CreateTableVersionEntry().id(a).version(1L).manifestPath("/a/v1");
      CreateTableVersionEntry e2 =
          new CreateTableVersionEntry().id(b).version(1L).manifestPath("/b/v1");

      assertThrowsWithCode(
          "batchCreateTableVersions(conflict)",
          () ->
              namespace.batchCreateTableVersions(
                  new BatchCreateTableVersionsRequest().entries(Arrays.asList(e1, e2))),
          ErrorCode.CONCURRENT_MODIFICATION);

      // table a version 1 must NOT have been created (atomicity)
      assertThrowsWithCode(
          "describeTableVersion(after failed batch, table a)",
          () ->
              namespace.describeTableVersion(
                  new DescribeTableVersionRequest().id(a).version(1L)),
          ErrorCode.TABLE_VERSION_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("batchDeleteTableVersions")
  class BatchDelete {

    @Test
    @DisplayName("removes a contiguous range of versions; gaps untouched")
    void deletesRange() {
      requires(Capability.VERSION_CREATE);
      requires(Capability.VERSION_BATCH_DELETE);
      requires(Capability.VERSION_DESCRIBE);

      List<String> tid = declareTable("vbd");
      for (long v = 1; v <= 5; v++) {
        namespace.createTableVersion(versionRequest(tid, v));
      }

      VersionRange range = new VersionRange().startVersion(2L).endVersion(4L);
      assertNotNull(
          namespace.batchDeleteTableVersions(
              new BatchDeleteTableVersionsRequest()
                  .id(tid)
                  .ranges(Collections.singletonList(range))));

      // 1 and 5 remain, 2..4 are gone
      assertNotNull(
          namespace.describeTableVersion(
              new DescribeTableVersionRequest().id(tid).version(1L)));
      assertNotNull(
          namespace.describeTableVersion(
              new DescribeTableVersionRequest().id(tid).version(5L)));
      assertThrowsWithCode(
          "describeTableVersion(deleted v3)",
          () ->
              namespace.describeTableVersion(
                  new DescribeTableVersionRequest().id(tid).version(3L)),
          ErrorCode.TABLE_VERSION_NOT_FOUND);
    }
  }
}
