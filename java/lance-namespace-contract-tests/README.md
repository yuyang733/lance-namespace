# lance-namespace-contract-tests

Reusable JUnit 5 contract test bases that pin the
[Lance Namespace specification](https://lance.org/format/namespace/).

A contract-test base is an abstract JUnit 5 class that exercises a single
slice of the spec (namespaces, table metadata, table data, indices, tags,
versions, transactions, errors). Each backend that implements
`org.lance.namespace.LanceNamespace` extends the bases that match the
slices it claims to support; the suite then verifies the backend behaves
exactly as the spec requires.

## What is in this module

| Base | Spec area | Capability prefix |
|---|---|---|
| `NamespaceOperationsContractTest` | namespace lifecycle | `NAMESPACE_*` |
| `TableMetadataOperationsContractTest` | metadata-only table ops | `TABLE_*` |
| `TableDataOperationsContractTest` | data-plane table ops (Arrow IPC) | `DATA_*` |
| `TableIndexOperationsContractTest` | vector / scalar indices | `INDEX_*` |
| `TableTagOperationsContractTest` | named tags on table versions | `TAG_*` |
| `TableVersionOperationsContractTest` | manifest-version metadata | `VERSION_*` |
| `TransactionOperationsContractTest` | transactions (negative paths) | `TXN_*` |
| `ErrorHandlingContractTest` | `ErrorCode` + typed exceptions | _backend-agnostic_ |

`Capability` is an enum of every opt-in operation. A backend that does
not support a capability overrides
`AbstractLanceNamespaceContractTest.supports(Capability)` to return
`false`; the corresponding tests are skipped via JUnit Assumptions
rather than failing.

`ContractAssertions` collects shared helpers (typed-exception
assertions, version-monotonicity checks, page-token termination
detection).

`AbstractLanceNamespaceContractTest` is the common base for all bases.
It owns the per-test Arrow `BufferAllocator`, builds the canonical
schema `(id INT32, name UTF8, vector FLOAT32[4])` and constructs Arrow
IPC payloads with `buildArrowIpc(rowCount)` for the data-plane bases.

## How to run the suite against a backend

```java
import org.lance.namespace.LanceNamespace;
import org.lance.namespace.contract.*;

public class MyBackendContractIT {

  static LanceNamespace freshNamespace() {
    return new MyBackend(/* unique root per call */);
  }

  public static class Namespaces extends NamespaceOperationsContractTest {
    @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
  }
  public static class TableMetadata extends TableMetadataOperationsContractTest {
    @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
  }
  public static class TableData extends TableDataOperationsContractTest {
    @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
  }
  public static class TableIndices extends TableIndexOperationsContractTest {
    @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
  }
  public static class TableTags extends TableTagOperationsContractTest {
    @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
  }
  public static class TableVersions extends TableVersionOperationsContractTest {
    @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
  }
  public static class Transactions extends TransactionOperationsContractTest {
    @Override protected LanceNamespace createNamespace() { return freshNamespace(); }
  }
}
```

Add the dependency:

```xml
<dependency>
  <groupId>org.lance</groupId>
  <artifactId>lance-namespace-contract-tests</artifactId>
  <version>${lance.version}</version>
  <scope>test</scope>
</dependency>
```

For backends that do not implement every operation, override
`supports(Capability)`:

```java
@Override
protected boolean supports(Capability capability) {
  switch (capability) {
    case NAMESPACE_CREATE:
    case NAMESPACE_DESCRIBE:
    case NAMESPACE_LIST:
    case TABLE_DECLARE:
    case TABLE_LIST:
      return true;
    default:
      return false;
  }
}
```

## Self-test

The module's own `src/test` directory contains a tiny in-memory
implementation (`InMemoryNamespace`) wired against the namespace and
table-metadata bases (`InMemoryNamespacesTest`,
`InMemoryTableMetadataTest`). They demonstrate that the bases pass
when the operations behave per spec, and act as a worked example for
backend authors.

Run them with:

```sh
mvn -pl lance-namespace-contract-tests test
```
