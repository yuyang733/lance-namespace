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
import org.lance.namespace.model.CreateNamespaceResponse;
import org.lance.namespace.model.DescribeNamespaceRequest;
import org.lance.namespace.model.DescribeNamespaceResponse;
import org.lance.namespace.model.DropNamespaceRequest;
import org.lance.namespace.model.ListNamespacesRequest;
import org.lance.namespace.model.ListNamespacesResponse;
import org.lance.namespace.model.NamespaceExistsRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lance.namespace.contract.ContractAssertions.assertThrowsWithCode;
import static org.lance.namespace.contract.ContractAssertions.isPageTokenTerminal;

/**
 * Contract tests for namespace lifecycle operations:
 *
 * <ul>
 *   <li>{@code listNamespaces} - paginated enumeration of children
 *   <li>{@code createNamespace} - with the three modes {@code CREATE}, {@code EXIST_OK},
 *       {@code OVERWRITE} as defined by the spec
 *   <li>{@code describeNamespace} - read-back of properties
 *   <li>{@code namespaceExists} - existence probe
 *   <li>{@code dropNamespace} - removal
 * </ul>
 *
 * <p>Reference: <a href="https://lance.org/format/namespace/">Lance Namespace specification</a>.
 */
public abstract class NamespaceOperationsContractTest extends AbstractLanceNamespaceContractTest {

  @Nested
  @DisplayName("createNamespace")
  class CreateNamespace {

    @Test
    @DisplayName("creates a brand-new namespace and returns supplied properties")
    void createsFreshNamespace() {
      requires(Capability.NAMESPACE_CREATE);

      String name = uniqueNamespace("create");
      Map<String, String> props = new HashMap<>();
      props.put("owner", "lance-contract");

      CreateNamespaceResponse resp =
          namespace.createNamespace(
              new CreateNamespaceRequest().id(id(name)).mode("CREATE").properties(props));
      assertNotNull(resp, "createNamespace response must not be null");
      if (resp.getProperties() != null) {
        assertEquals(
            "lance-contract",
            resp.getProperties().get("owner"),
            "createNamespace must echo back supplied properties");
      }
    }

    @Test
    @DisplayName("with mode=CREATE on an existing namespace raises NAMESPACE_ALREADY_EXISTS")
    void createTwiceRaisesAlreadyExists() {
      requires(Capability.NAMESPACE_CREATE);

      String name = uniqueNamespace("dup");
      namespace.createNamespace(new CreateNamespaceRequest().id(id(name)).mode("CREATE"));
      assertThrowsWithCode(
          "createNamespace(duplicate, mode=CREATE)",
          () ->
              namespace.createNamespace(
                  new CreateNamespaceRequest().id(id(name)).mode("CREATE")),
          ErrorCode.NAMESPACE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("with mode=EXIST_OK on an existing namespace is idempotent")
    void createExistOkIsIdempotent() {
      requires(Capability.NAMESPACE_CREATE);

      String name = uniqueNamespace("ex_ok");
      namespace.createNamespace(new CreateNamespaceRequest().id(id(name)).mode("CREATE"));
      assertDoesNotThrow(
          () ->
              namespace.createNamespace(
                  new CreateNamespaceRequest().id(id(name)).mode("EXIST_OK")),
          "mode=EXIST_OK must succeed when the namespace already exists");
    }

    @Test
    @DisplayName("with mode=OVERWRITE on an existing namespace replaces its state")
    void overwriteReplacesExisting() {
      requires(Capability.NAMESPACE_CREATE);
      requires(Capability.NAMESPACE_DESCRIBE);

      String name = uniqueNamespace("ow");
      Map<String, String> first = new HashMap<>();
      first.put("k", "v1");
      namespace.createNamespace(
          new CreateNamespaceRequest().id(id(name)).mode("CREATE").properties(first));

      Map<String, String> second = new HashMap<>();
      second.put("k", "v2");
      namespace.createNamespace(
          new CreateNamespaceRequest().id(id(name)).mode("OVERWRITE").properties(second));

      DescribeNamespaceResponse desc =
          namespace.describeNamespace(new DescribeNamespaceRequest().id(id(name)));
      if (desc.getProperties() != null) {
        assertEquals(
            "v2",
            desc.getProperties().get("k"),
            "OVERWRITE must replace the existing namespace's properties");
      }
    }
  }

  @Nested
  @DisplayName("describeNamespace")
  class DescribeNamespace {

    @Test
    @DisplayName("returns properties of a previously-created namespace")
    void describeAfterCreate() {
      requires(Capability.NAMESPACE_CREATE);
      requires(Capability.NAMESPACE_DESCRIBE);

      String name = uniqueNamespace("desc");
      Map<String, String> props = new HashMap<>();
      props.put("alpha", "1");
      namespace.createNamespace(
          new CreateNamespaceRequest().id(id(name)).mode("CREATE").properties(props));

      DescribeNamespaceResponse resp =
          namespace.describeNamespace(new DescribeNamespaceRequest().id(id(name)));
      assertNotNull(resp, "describeNamespace must return non-null");
      if (resp.getProperties() != null) {
        assertEquals("1", resp.getProperties().get("alpha"));
      }
    }

    @Test
    @DisplayName("on a missing namespace raises NAMESPACE_NOT_FOUND")
    void describeMissing() {
      requires(Capability.NAMESPACE_DESCRIBE);

      assertThrowsWithCode(
          "describeNamespace(missing)",
          () ->
              namespace.describeNamespace(
                  new DescribeNamespaceRequest().id(id(uniqueNamespace("ghost")))),
          ErrorCode.NAMESPACE_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("namespaceExists")
  class NamespaceExists {

    @Test
    @DisplayName("succeeds for an existing namespace and raises NOT_FOUND for a missing one")
    void existsMatrix() {
      requires(Capability.NAMESPACE_CREATE);
      requires(Capability.NAMESPACE_EXISTS);

      String present = uniqueNamespace("exists");
      namespace.createNamespace(new CreateNamespaceRequest().id(id(present)).mode("CREATE"));

      assertDoesNotThrow(
          () -> namespace.namespaceExists(new NamespaceExistsRequest().id(id(present))),
          "namespaceExists must succeed for a present namespace");

      assertThrowsWithCode(
          "namespaceExists(missing)",
          () ->
              namespace.namespaceExists(
                  new NamespaceExistsRequest().id(id(uniqueNamespace("ghost")))),
          ErrorCode.NAMESPACE_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("listNamespaces")
  class ListNamespaces {

    @Test
    @DisplayName("includes the just-created namespace; pagination terminates")
    void listAfterCreate() {
      requires(Capability.NAMESPACE_CREATE);
      requires(Capability.NAMESPACE_LIST);

      String visible = uniqueNamespace("seen");
      namespace.createNamespace(new CreateNamespaceRequest().id(id(visible)).mode("CREATE"));

      String pageToken = null;
      boolean found = false;
      int safety = 100;
      do {
        ListNamespacesRequest req = new ListNamespacesRequest();
        if (pageToken != null) {
          req.setPageToken(pageToken);
        }
        ListNamespacesResponse resp = namespace.listNamespaces(req);
        assertNotNull(resp, "listNamespaces response must not be null");
        Set<String> namespaces = resp.getNamespaces();
        assertNotNull(
            namespaces, "ListNamespacesResponse.namespaces must always be populated (per spec)");
        if (namespaces.contains(visible)) {
          found = true;
        }
        pageToken = resp.getPageToken();
        safety--;
      } while (!isPageTokenTerminal(pageToken) && safety > 0);

      assertTrue(safety > 0, "listNamespaces pagination must terminate");
      assertTrue(found, "listNamespaces must include the just-created namespace " + visible);
    }
  }

  @Nested
  @DisplayName("dropNamespace")
  class DropNamespace {

    @Test
    @DisplayName("removes a namespace; subsequent describe raises NAMESPACE_NOT_FOUND")
    void dropAndDescribe() {
      requires(Capability.NAMESPACE_CREATE);
      requires(Capability.NAMESPACE_DROP);
      requires(Capability.NAMESPACE_DESCRIBE);

      String name = uniqueNamespace("drop");
      namespace.createNamespace(new CreateNamespaceRequest().id(id(name)).mode("CREATE"));
      assertNotNull(namespace.dropNamespace(new DropNamespaceRequest().id(id(name))));

      assertThrowsWithCode(
          "describeNamespace(after drop)",
          () -> namespace.describeNamespace(new DescribeNamespaceRequest().id(id(name))),
          ErrorCode.NAMESPACE_NOT_FOUND);
    }

    @Test
    @DisplayName("on a missing namespace raises NAMESPACE_NOT_FOUND")
    void dropMissing() {
      requires(Capability.NAMESPACE_DROP);

      List<String> ghost = id(uniqueNamespace("ghost"));
      assertThrowsWithCode(
          "dropNamespace(missing)",
          () -> namespace.dropNamespace(new DropNamespaceRequest().id(ghost)),
          ErrorCode.NAMESPACE_NOT_FOUND);
    }
  }
}
