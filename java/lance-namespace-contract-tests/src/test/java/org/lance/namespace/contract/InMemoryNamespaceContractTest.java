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

import java.util.Collections;

/**
 * Self-test helpers for the contract-test bases.
 *
 * <p>The two outer classes {@code InMemoryNamespaceNamespacesTest} and
 * {@code InMemoryNamespaceTableMetadataTest} (sibling files) wire the in-process
 * {@link InMemoryNamespace} reference implementation against the bases for the slices it actually
 * implements (namespace lifecycle + table metadata). All other capabilities are reported as
 * unsupported, so their tests are skipped via JUnit Assumptions.
 *
 * <p>If a contract base raises a spec-conformance failure on this in-memory implementation, the
 * regression is in the bases themselves, not in any external backend.
 */
final class InMemoryNamespaceContractTest {

  private InMemoryNamespaceContractTest() {}

  static LanceNamespace freshNamespace() {
    InMemoryNamespace ns = new InMemoryNamespace();
    ns.initialize(Collections.<String, String>emptyMap(), null);
    return ns;
  }

  /** Capabilities actually implemented by {@link InMemoryNamespace}. */
  static boolean inMemorySupports(Capability capability) {
    switch (capability) {
      case NAMESPACE_LIST:
      case NAMESPACE_DESCRIBE:
      case NAMESPACE_CREATE:
      case NAMESPACE_DROP:
      case NAMESPACE_EXISTS:
      case TABLE_LIST:
      case TABLE_DESCRIBE:
      case TABLE_EXISTS:
      case TABLE_DECLARE:
      case TABLE_DROP:
      case TABLE_RENAME:
        return true;
      default:
        return false;
    }
  }
}