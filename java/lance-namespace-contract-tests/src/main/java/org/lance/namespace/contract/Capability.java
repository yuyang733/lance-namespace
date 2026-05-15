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

/**
 * Optional capabilities defined by the Lance Namespace specification.
 *
 * <p>The Lance Namespace spec is intentionally layered: every operation is opt-in. Backends
 * implement the slices they can support and surface the rest as
 * {@link org.lance.namespace.errors.UnsupportedOperationException}.
 *
 * <p>Contract test bases gate every test on a capability via
 * {@link AbstractLanceNamespaceContractTest#requires(Capability)}. A backend declares which
 * capabilities it supports by overriding
 * {@link AbstractLanceNamespaceContractTest#supports(Capability)}; tests for unsupported
 * capabilities are silently skipped (JUnit 5 {@code Assumption} failures).
 */
public enum Capability {
  // ---- Namespace lifecycle ----
  NAMESPACE_LIST,
  NAMESPACE_DESCRIBE,
  NAMESPACE_CREATE,
  NAMESPACE_DROP,
  NAMESPACE_EXISTS,

  // ---- Table metadata plane ----
  TABLE_LIST,
  TABLE_DESCRIBE,
  TABLE_EXISTS,
  TABLE_DECLARE,
  TABLE_DROP,
  TABLE_DEREGISTER,
  TABLE_REGISTER,
  TABLE_RENAME,
  TABLE_RESTORE,

  // ---- Table data plane ----
  DATA_CREATE_TABLE,
  DATA_INSERT,
  DATA_MERGE_INSERT,
  DATA_UPDATE,
  DATA_DELETE,
  DATA_QUERY,
  DATA_COUNT_ROWS,

  // ---- Table indices ----
  INDEX_CREATE_VECTOR,
  INDEX_CREATE_SCALAR,
  INDEX_LIST,
  INDEX_STATS,
  INDEX_DROP,

  // ---- Table tags ----
  TAG_CREATE,
  TAG_LIST,
  TAG_GET_VERSION,
  TAG_UPDATE,
  TAG_DELETE,

  // ---- Table version manifest ----
  VERSION_LIST,
  VERSION_DESCRIBE,
  VERSION_CREATE,
  VERSION_BATCH_CREATE,
  VERSION_BATCH_DELETE,

  // ---- Transactions ----
  TXN_DESCRIBE,
  TXN_ALTER
}
