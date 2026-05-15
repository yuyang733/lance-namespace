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
 * Catalog of every contract-test base in this module, intended as a one-stop reference for
 * backend authors. To run the full conformance suite, declare one concrete subclass per base
 * (typically as nested classes annotated with {@code @Nested}), e.g.:
 *
 * <pre>{@code
 * public class MyBackendContractIT {
 *
 *   private static LanceNamespace freshNamespace() {
 *     return new MyBackend(...uniqueRoot()...);
 *   }
 *
 *   public static class Namespaces extends NamespaceOperationsContractTest {
 *     @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
 *   }
 *   public static class TableMetadata extends TableMetadataOperationsContractTest {
 *     @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
 *   }
 *   public static class TableData extends TableDataOperationsContractTest {
 *     @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
 *   }
 *   public static class TableIndices extends TableIndexOperationsContractTest {
 *     @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
 *   }
 *   public static class TableTags extends TableTagOperationsContractTest {
 *     @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
 *   }
 *   public static class TableVersions extends TableVersionOperationsContractTest {
 *     @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
 *   }
 *   public static class Transactions extends TransactionOperationsContractTest {
 *     @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
 *   }
 *   // ErrorHandlingContractTest is backend-agnostic - extend it directly to opt in.
 * }
 * }</pre>
 *
 * <h2>Capabilities</h2>
 *
 * Each operation is opt-in. Backends that do not support a capability override
 * {@link AbstractLanceNamespaceContractTest#supports(Capability)} to return {@code false}; the
 * corresponding tests are skipped via JUnit 5
 * {@link org.junit.jupiter.api.Assumptions} rather than failing.
 *
 * <h2>Exhaustive list of bases</h2>
 *
 * <ul>
 *   <li>{@link NamespaceOperationsContractTest}
 *   <li>{@link TableMetadataOperationsContractTest}
 *   <li>{@link TableDataOperationsContractTest}
 *   <li>{@link TableIndexOperationsContractTest}
 *   <li>{@link TableTagOperationsContractTest}
 *   <li>{@link TableVersionOperationsContractTest}
 *   <li>{@link TransactionOperationsContractTest}
 *   <li>{@link ErrorHandlingContractTest} (backend-agnostic; concrete class)
 * </ul>
 */
public final class FullContractSuite {
  private FullContractSuite() {}
}
