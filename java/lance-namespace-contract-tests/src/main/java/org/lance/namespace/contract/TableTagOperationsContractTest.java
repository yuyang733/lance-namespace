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
import org.lance.namespace.model.CreateTableRequest;
import org.lance.namespace.model.CreateTableResponse;
import org.lance.namespace.model.CreateTableTagRequest;
import org.lance.namespace.model.DeleteTableTagRequest;
import org.lance.namespace.model.GetTableTagVersionRequest;
import org.lance.namespace.model.GetTableTagVersionResponse;
import org.lance.namespace.model.InsertIntoTableRequest;
import org.lance.namespace.model.ListTableTagsRequest;
import org.lance.namespace.model.ListTableTagsResponse;
import org.lance.namespace.model.TagContents;
import org.lance.namespace.model.UpdateTableTagRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lance.namespace.contract.ContractAssertions.assertThrowsWithCode;

/**
 * Contract tests for table tag operations:
 *
 * <ul>
 *   <li>{@code createTableTag} / {@code updateTableTag} / {@code deleteTableTag}
 *   <li>{@code getTableTagVersion} - resolve a tag to a version number
 *   <li>{@code listTableTags} - paginated enumeration of tags
 * </ul>
 *
 * <p>Tags are immutable references to specific versions of a table; updating a tag rebinds it
 * to a different version, and deleting drops only the tag (not the data).
 */
public abstract class TableTagOperationsContractTest extends AbstractLanceNamespaceContractTest {

  protected List<String> ensureParentNamespace(String prefix) {
    if (supports(Capability.NAMESPACE_CREATE)) {
      String ns = uniqueNamespace(prefix);
      namespace.createNamespace(new CreateNamespaceRequest().id(id(ns)).mode("CREATE"));
      return id(ns);
    }
    return id();
  }

  /**
   * Bootstrap a populated table and return its identifier together with two distinct version
   * numbers obtained by an initial create followed by an append.
   */
  protected TaggedTable bootstrapTaggedTable(String prefix) {
    requires(Capability.DATA_CREATE_TABLE);

    List<String> parent = ensureParentNamespace(prefix);
    List<String> tid = child(parent, uniqueTable(prefix));

    CreateTableResponse created =
        namespace.createTable(
            new CreateTableRequest().id(tid).mode("CREATE"), buildArrowIpc(3));
    Long v1 = created.getVersion();
    assertNotNull(v1, "createTable must return a version");

    Long v2 = v1;
    if (supports(Capability.DATA_INSERT)) {
      namespace.insertIntoTable(
          new InsertIntoTableRequest().id(tid).mode("append"), buildArrowIpc(2));
      // version after append - rely on tag tests not requiring strict v2 > v1 if a backend
      // does not surface intermediate versions; we still pass v1 as a known-valid version.
      v2 = v1 + 1;
    }
    return new TaggedTable(tid, v1, v2);
  }

  /** Holder for a bootstrapped table together with two known-valid version numbers. */
  protected static final class TaggedTable {
    final List<String> id;
    final Long v1;
    final Long v2;

    TaggedTable(List<String> id, Long v1, Long v2) {
      this.id = id;
      this.v1 = v1;
      this.v2 = v2;
    }
  }

  @Nested
  @DisplayName("createTableTag")
  class CreateTag {

    @Test
    @DisplayName("creates a tag pointing to a known version")
    void createsTag() {
      requires(Capability.TAG_CREATE);
      TaggedTable t = bootstrapTaggedTable("tagc");

      assertNotNull(
          namespace.createTableTag(
              new CreateTableTagRequest().id(t.id).tag("v1").version(t.v1)));
    }

    @Test
    @DisplayName("creating a duplicate tag raises TABLE_TAG_ALREADY_EXISTS")
    void duplicateTagRaises() {
      requires(Capability.TAG_CREATE);
      TaggedTable t = bootstrapTaggedTable("tagcd");

      namespace.createTableTag(
          new CreateTableTagRequest().id(t.id).tag("dup").version(t.v1));
      assertThrowsWithCode(
          "createTableTag(duplicate)",
          () ->
              namespace.createTableTag(
                  new CreateTableTagRequest().id(t.id).tag("dup").version(t.v1)),
          ErrorCode.TABLE_TAG_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("creating a tag pointing to a missing version raises TABLE_VERSION_NOT_FOUND")
    void missingVersionRaises() {
      requires(Capability.TAG_CREATE);
      TaggedTable t = bootstrapTaggedTable("tagcv");

      assertThrowsWithCode(
          "createTableTag(missing version)",
          () ->
              namespace.createTableTag(
                  new CreateTableTagRequest().id(t.id).tag("ghost").version(99999L)),
          ErrorCode.TABLE_VERSION_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("getTableTagVersion")
  class GetTagVersion {

    @Test
    @DisplayName("returns the version originally bound to the tag")
    void getsVersion() {
      requires(Capability.TAG_CREATE);
      requires(Capability.TAG_GET_VERSION);
      TaggedTable t = bootstrapTaggedTable("tagg");

      namespace.createTableTag(
          new CreateTableTagRequest().id(t.id).tag("g").version(t.v1));
      GetTableTagVersionResponse resp =
          namespace.getTableTagVersion(new GetTableTagVersionRequest().id(t.id).tag("g"));
      assertNotNull(resp);
      assertEquals(t.v1, resp.getVersion(), "tag must resolve to the version it was created with");
    }

    @Test
    @DisplayName("getting a missing tag raises TABLE_TAG_NOT_FOUND")
    void missingTagRaises() {
      requires(Capability.TAG_GET_VERSION);
      TaggedTable t = bootstrapTaggedTable("taggm");

      assertThrowsWithCode(
          "getTableTagVersion(missing)",
          () ->
              namespace.getTableTagVersion(
                  new GetTableTagVersionRequest().id(t.id).tag("__nonexistent__")),
          ErrorCode.TABLE_TAG_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("listTableTags")
  class ListTags {

    @Test
    @DisplayName("includes the tag just created")
    void listsTags() {
      requires(Capability.TAG_CREATE);
      requires(Capability.TAG_LIST);
      TaggedTable t = bootstrapTaggedTable("tagl");

      namespace.createTableTag(
          new CreateTableTagRequest().id(t.id).tag("listed").version(t.v1));

      ListTableTagsResponse resp =
          namespace.listTableTags(new ListTableTagsRequest().id(t.id));
      assertNotNull(resp);
      Map<String, TagContents> tags = resp.getTags();
      assertNotNull(tags, "ListTableTagsResponse.tags must not be null");
      assertTrue(tags.containsKey("listed"), "listTableTags must include the created tag");
      TagContents content = tags.get("listed");
      assertNotNull(content);
      assertEquals(t.v1, content.getVersion(), "tag content must reference the bound version");
    }
  }

  @Nested
  @DisplayName("updateTableTag")
  class UpdateTag {

    @Test
    @DisplayName("rebinds the tag to a different version")
    void updatesTag() {
      requires(Capability.TAG_CREATE);
      requires(Capability.TAG_UPDATE);
      requires(Capability.TAG_GET_VERSION);
      TaggedTable t = bootstrapTaggedTable("tagu");
      // Skip if the backend did not produce two distinct versions
      org.junit.jupiter.api.Assumptions.assumeTrue(
          !t.v1.equals(t.v2),
          "Backend produced only one version; cannot test tag rebind.");

      namespace.createTableTag(
          new CreateTableTagRequest().id(t.id).tag("mv").version(t.v1));
      assertNotNull(
          namespace.updateTableTag(
              new UpdateTableTagRequest().id(t.id).tag("mv").version(t.v2)));

      GetTableTagVersionResponse resp =
          namespace.getTableTagVersion(new GetTableTagVersionRequest().id(t.id).tag("mv"));
      assertEquals(
          t.v2, resp.getVersion(), "after update, tag must point at the new version");
    }

    @Test
    @DisplayName("updating a missing tag raises TABLE_TAG_NOT_FOUND")
    void missingTagRaises() {
      requires(Capability.TAG_UPDATE);
      TaggedTable t = bootstrapTaggedTable("tagum");

      assertThrowsWithCode(
          "updateTableTag(missing)",
          () ->
              namespace.updateTableTag(
                  new UpdateTableTagRequest().id(t.id).tag("ghost").version(t.v1)),
          ErrorCode.TABLE_TAG_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("deleteTableTag")
  class DeleteTag {

    @Test
    @DisplayName("removes the tag; subsequent get raises TABLE_TAG_NOT_FOUND")
    void deletesTag() {
      requires(Capability.TAG_CREATE);
      requires(Capability.TAG_DELETE);
      requires(Capability.TAG_GET_VERSION);
      TaggedTable t = bootstrapTaggedTable("tagd");

      namespace.createTableTag(
          new CreateTableTagRequest().id(t.id).tag("d").version(t.v1));
      assertNotNull(
          namespace.deleteTableTag(new DeleteTableTagRequest().id(t.id).tag("d")));

      assertThrowsWithCode(
          "getTableTagVersion(after delete)",
          () ->
              namespace.getTableTagVersion(
                  new GetTableTagVersionRequest().id(t.id).tag("d")),
          ErrorCode.TABLE_TAG_NOT_FOUND);
    }

    @Test
    @DisplayName("deleting a missing tag raises TABLE_TAG_NOT_FOUND")
    void missingTagRaises() {
      requires(Capability.TAG_DELETE);
      TaggedTable t = bootstrapTaggedTable("tagdm");

      assertThrowsWithCode(
          "deleteTableTag(missing)",
          () ->
              namespace.deleteTableTag(
                  new DeleteTableTagRequest().id(t.id).tag("__nonexistent__")),
          ErrorCode.TABLE_TAG_NOT_FOUND);
    }
  }
}
