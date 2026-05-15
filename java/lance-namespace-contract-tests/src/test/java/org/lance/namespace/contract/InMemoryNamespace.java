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

import org.lance.namespace.LanceNamespace;
import org.lance.namespace.errors.NamespaceAlreadyExistsException;
import org.lance.namespace.errors.NamespaceNotFoundException;
import org.lance.namespace.errors.TableAlreadyExistsException;
import org.lance.namespace.errors.TableNotFoundException;
import org.lance.namespace.model.CreateNamespaceRequest;
import org.lance.namespace.model.CreateNamespaceResponse;
import org.lance.namespace.model.DeclareTableRequest;
import org.lance.namespace.model.DeclareTableResponse;
import org.lance.namespace.model.DescribeNamespaceRequest;
import org.lance.namespace.model.DescribeNamespaceResponse;
import org.lance.namespace.model.DescribeTableRequest;
import org.lance.namespace.model.DescribeTableResponse;
import org.lance.namespace.model.DropNamespaceRequest;
import org.lance.namespace.model.DropNamespaceResponse;
import org.lance.namespace.model.DropTableRequest;
import org.lance.namespace.model.DropTableResponse;
import org.lance.namespace.model.ListNamespacesRequest;
import org.lance.namespace.model.ListNamespacesResponse;
import org.lance.namespace.model.ListTablesRequest;
import org.lance.namespace.model.ListTablesResponse;
import org.lance.namespace.model.NamespaceExistsRequest;
import org.lance.namespace.model.RenameTableRequest;
import org.lance.namespace.model.RenameTableResponse;
import org.lance.namespace.model.TableExistsRequest;

import org.apache.arrow.memory.BufferAllocator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory implementation of the namespace + table-metadata slice.
 *
 * <p>This class exists purely to self-test the contract-test bases in this module: it implements
 * just enough of the spec to demonstrate that the bases pass when the operations behave per
 * spec, and it serves as a tiny worked example for backend authors.
 *
 * <p>It is NOT a production implementation. It does not support data, indices, tags, versions,
 * transactions, or pagination beyond a single page.
 */
final class InMemoryNamespace implements LanceNamespace {

  /** Each namespace tracks its own properties and the set of tables declared inside it. */
  private static final class NsEntry {
    final Map<String, String> properties = new ConcurrentHashMap<>();
    final Map<String, Object> tables = new ConcurrentHashMap<>();
  }

  private final Map<String, NsEntry> namespaces = new ConcurrentHashMap<>();
  private final String id = UUID.randomUUID().toString();

  @Override
  public void initialize(Map<String, String> configProperties, BufferAllocator allocator) {
    // No-op; everything is held in memory.
  }

  @Override
  public String namespaceId() {
    return "in-memory:" + id;
  }

  // ---------- Namespace ops ----------

  private static String key(List<String> id) {
    if (id == null || id.isEmpty()) {
      return "";
    }
    return String.join("/", id);
  }

  private NsEntry requireNs(List<String> id) {
    NsEntry ns = namespaces.get(key(id));
    if (ns == null) {
      throw new NamespaceNotFoundException("Namespace not found: " + id);
    }
    return ns;
  }

  @Override
  public CreateNamespaceResponse createNamespace(CreateNamespaceRequest request) {
    String k = key(request.getId());
    String mode = request.getMode() == null ? "CREATE" : request.getMode();
    boolean exists = namespaces.containsKey(k);
    NsEntry ns;
    switch (mode) {
      case "EXIST_OK":
        ns = namespaces.computeIfAbsent(k, s -> new NsEntry());
        break;
      case "OVERWRITE":
        ns = new NsEntry();
        namespaces.put(k, ns);
        break;
      case "CREATE":
      default:
        if (exists) {
          throw new NamespaceAlreadyExistsException("Namespace already exists: " + request.getId());
        }
        ns = new NsEntry();
        namespaces.put(k, ns);
        break;
    }
    if (request.getProperties() != null) {
      ns.properties.clear();
      ns.properties.putAll(request.getProperties());
    }
    CreateNamespaceResponse resp = new CreateNamespaceResponse();
    resp.setProperties(new HashMap<>(ns.properties));
    return resp;
  }

  @Override
  public DescribeNamespaceResponse describeNamespace(DescribeNamespaceRequest request) {
    NsEntry ns = requireNs(request.getId());
    DescribeNamespaceResponse resp = new DescribeNamespaceResponse();
    resp.setProperties(new HashMap<>(ns.properties));
    return resp;
  }

  @Override
  public void namespaceExists(NamespaceExistsRequest request) {
    requireNs(request.getId());
  }

  @Override
  public ListNamespacesResponse listNamespaces(ListNamespacesRequest request) {
    String parentKey = key(request.getId());
    LinkedHashSet<String> children = new LinkedHashSet<>();
    for (String name : namespaces.keySet()) {
      if (parentKey.isEmpty()) {
        if (!name.contains("/")) {
          children.add(name);
        }
      } else if (name.startsWith(parentKey + "/")) {
        String tail = name.substring(parentKey.length() + 1);
        if (!tail.contains("/")) {
          children.add(tail);
        }
      }
    }
    ListNamespacesResponse resp = new ListNamespacesResponse();
    resp.setNamespaces(children);
    // Single-page implementation -> terminal page token (null).
    return resp;
  }

  @Override
  public DropNamespaceResponse dropNamespace(DropNamespaceRequest request) {
    String k = key(request.getId());
    if (namespaces.remove(k) == null) {
      throw new NamespaceNotFoundException("Namespace not found: " + request.getId());
    }
    return new DropNamespaceResponse();
  }

  // ---------- Table-metadata ops ----------

  /** Split an absolute table id into (parentKey, tableName). */
  private static String[] split(List<String> id) {
    if (id == null || id.isEmpty()) {
      throw new TableNotFoundException("Table id must not be empty");
    }
    String name = id.get(id.size() - 1);
    String parent = id.size() == 1 ? "" : String.join("/", id.subList(0, id.size() - 1));
    return new String[] {parent, name};
  }

  private NsEntry requireParent(List<String> id) {
    String[] parts = split(id);
    NsEntry ns = namespaces.get(parts[0]);
    if (ns == null) {
      throw new TableNotFoundException("Parent namespace not found for table: " + id);
    }
    return ns;
  }

  @Override
  public DeclareTableResponse declareTable(DeclareTableRequest request) {
    String[] parts = split(request.getId());
    NsEntry ns = requireParent(request.getId());
    if (ns.tables.containsKey(parts[1])) {
      throw new TableAlreadyExistsException("Table already exists: " + request.getId());
    }
    ns.tables.put(parts[1], new LinkedHashMap<String, Object>());
    return new DeclareTableResponse();
  }

  @Override
  public DescribeTableResponse describeTable(DescribeTableRequest request) {
    String[] parts = split(request.getId());
    NsEntry ns = requireParent(request.getId());
    if (!ns.tables.containsKey(parts[1])) {
      throw new TableNotFoundException("Table not found: " + request.getId());
    }
    return new DescribeTableResponse();
  }

  @Override
  public void tableExists(TableExistsRequest request) {
    String[] parts = split(request.getId());
    NsEntry ns = requireParent(request.getId());
    if (!ns.tables.containsKey(parts[1])) {
      throw new TableNotFoundException("Table not found: " + request.getId());
    }
  }

  @Override
  public ListTablesResponse listTables(ListTablesRequest request) {
    NsEntry ns = requireNs(request.getId());
    LinkedHashSet<String> names = new LinkedHashSet<>(ns.tables.keySet());
    Integer limit = request.getLimit();
    if (limit != null && limit >= 0 && names.size() > limit) {
      LinkedHashSet<String> capped = new LinkedHashSet<>();
      int i = 0;
      for (String n : names) {
        if (i++ >= limit) {
          break;
        }
        capped.add(n);
      }
      names = capped;
    }
    ListTablesResponse resp = new ListTablesResponse();
    resp.setTables(names);
    return resp;
  }

  @Override
  public DropTableResponse dropTable(DropTableRequest request) {
    String[] parts = split(request.getId());
    NsEntry ns = requireParent(request.getId());
    if (ns.tables.remove(parts[1]) == null) {
      throw new TableNotFoundException("Table not found: " + request.getId());
    }
    return new DropTableResponse();
  }

  @Override
  public RenameTableResponse renameTable(RenameTableRequest request) {
    String[] parts = split(request.getId());
    NsEntry ns = requireParent(request.getId());
    Object payload = ns.tables.remove(parts[1]);
    if (payload == null) {
      throw new TableNotFoundException("Table not found: " + request.getId());
    }
    String newName = request.getNewTableName();
    if (ns.tables.containsKey(newName)) {
      // Restore the old binding before raising.
      ns.tables.put(parts[1], payload);
      throw new TableAlreadyExistsException(
          "Cannot rename to existing table name: " + newName);
    }
    ns.tables.put(newName, payload);
    return new RenameTableResponse();
  }
}
