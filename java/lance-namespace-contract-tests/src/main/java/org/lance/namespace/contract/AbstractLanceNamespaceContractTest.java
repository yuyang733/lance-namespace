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
import org.lance.namespace.model.JsonArrowDataType;
import org.lance.namespace.model.JsonArrowField;
import org.lance.namespace.model.JsonArrowSchema;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.complex.FixedSizeListVector;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Common base for every Lance Namespace contract test.
 *
 * <p>Subclass one of the {@code *OperationsContractTest} classes (e.g.
 * {@link NamespaceOperationsContractTest}) once per backend and override:
 *
 * <ol>
 *   <li>{@link #createNamespace()} - returns a freshly initialized {@link LanceNamespace} that is
 *       isolated from any state created by previous tests (give each test a unique root path or
 *       in-memory store).
 *   <li>{@link #supports(Capability)} - declare which capabilities the backend implements; tests
 *       for unsupported capabilities are skipped via JUnit 5
 *       {@link org.junit.jupiter.api.Assumptions} rather than failing.
 * </ol>
 *
 * <p>Per Lance Namespace specification (https://lance.org/format/namespace/), all operations are
 * optional. A backend that only implements the namespace lifecycle is conformant - it just needs
 * to opt out of the operations it does not implement.
 */
public abstract class AbstractLanceNamespaceContractTest {

  /** Standard 4-dimensional vector field used by data-plane tests. */
  protected static final int VECTOR_DIM = 4;

  /** Per-test Arrow allocator. Closed automatically in {@link #tearDownAbstract()}. */
  protected BufferAllocator allocator;

  /** Namespace instance under test, freshly built before each test. */
  protected LanceNamespace namespace;

  /** Build the namespace implementation under test. */
  protected abstract LanceNamespace createNamespace();

  /**
   * Declare whether the implementation under test supports the given capability.
   *
   * <p>Default: {@code true} for every capability. Override to disable contract tests for
   * operations that the backend cannot honor.
   */
  protected boolean supports(Capability capability) {
    return true;
  }

  /** Skip the current test if the implementation does not support the supplied capability. */
  protected final void requires(Capability capability) {
    Assumptions.assumeTrue(
        supports(capability),
        "Implementation does not support capability " + capability + " - skipping contract test");
  }

  @BeforeEach
  void setUpAbstract() {
    this.allocator = new RootAllocator(Long.MAX_VALUE);
    this.namespace = createNamespace();
  }

  @AfterEach
  void tearDownAbstract() {
    if (allocator != null) {
      allocator.close();
    }
  }

  // =========================================================================================
  // Naming helpers
  // =========================================================================================

  /** Generate a fresh namespace name unique to this JVM run. */
  protected String uniqueNamespace(String prefix) {
    return prefix + "_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  /** Generate a fresh table name unique to this JVM run. */
  protected String uniqueTable(String prefix) {
    return prefix + "_tbl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  /** Build an empty identifier list. */
  protected List<String> id() {
    return new ArrayList<>();
  }

  /** Build a multi-segment identifier list. */
  protected List<String> id(String... segments) {
    return new ArrayList<>(Arrays.asList(segments));
  }

  /** Append a child segment to an existing identifier list. */
  protected List<String> child(List<String> parent, String segment) {
    List<String> result = new ArrayList<>(parent);
    result.add(segment);
    return result;
  }

  // =========================================================================================
  // Schema helpers - canonical schema (id INT32, name UTF8, vector FLOAT32[4]) used by all
  // data-plane tests so every backend is exercised against the same shape.
  // =========================================================================================

  /**
   * Return the canonical contract-test schema:
   * {@code id: int32, name: utf8, vector: float32[4]}.
   */
  protected JsonArrowSchema canonicalJsonSchema() {
    JsonArrowSchema schema = new JsonArrowSchema();

    JsonArrowField idField = new JsonArrowField();
    idField.setName("id");
    idField.setNullable(false);
    JsonArrowDataType idType = new JsonArrowDataType();
    idType.setType("int32");
    idField.setType(idType);

    JsonArrowField nameField = new JsonArrowField();
    nameField.setName("name");
    nameField.setNullable(true);
    JsonArrowDataType nameType = new JsonArrowDataType();
    nameType.setType("utf8");
    nameField.setType(nameType);

    JsonArrowField vectorField = new JsonArrowField();
    vectorField.setName("vector");
    vectorField.setNullable(false);
    JsonArrowDataType vectorType = new JsonArrowDataType();
    vectorType.setType("fixed_size_list");
    vectorType.setLength((long) VECTOR_DIM);

    JsonArrowField vectorChild = new JsonArrowField();
    vectorChild.setName("item");
    vectorChild.setNullable(false);
    JsonArrowDataType vectorChildType = new JsonArrowDataType();
    vectorChildType.setType("float32");
    vectorChild.setType(vectorChildType);
    vectorType.setFields(Collections.singletonList(vectorChild));
    vectorField.setType(vectorType);

    schema.setFields(Arrays.asList(idField, nameField, vectorField));
    return schema;
  }

  /** Arrow Schema mirror of {@link #canonicalJsonSchema()} for IPC payload construction. */
  protected Schema canonicalArrowSchema() {
    Field idField = new Field("id", FieldType.notNullable(new ArrowType.Int(32, true)), null);
    Field nameField = new Field("name", FieldType.nullable(new ArrowType.Utf8()), null);
    Field vectorChild =
        new Field(
            "item",
            FieldType.notNullable(new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)),
            null);
    Field vectorField =
        new Field(
            "vector",
            FieldType.notNullable(new ArrowType.FixedSizeList(VECTOR_DIM)),
            Collections.singletonList(vectorChild));
    return new Schema(Arrays.asList(idField, nameField, vectorField));
  }

  /**
   * Build an Arrow IPC stream containing {@code rowCount} rows of canonical-schema data. Used by
   * data-plane contract tests that exercise {@code createTable}, {@code insertIntoTable}, etc.
   */
  protected byte[] buildArrowIpc(int rowCount) {
    Schema schema = canonicalArrowSchema();
    try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
      IntVector idVec = (IntVector) root.getVector("id");
      VarCharVector nameVec = (VarCharVector) root.getVector("name");
      FixedSizeListVector vecVec = (FixedSizeListVector) root.getVector("vector");
      Float4Vector vecChild = (Float4Vector) vecVec.getDataVector();

      idVec.allocateNew(rowCount);
      nameVec.allocateNew(rowCount);
      vecVec.allocateNew();
      vecChild.allocateNew(rowCount * VECTOR_DIM);

      for (int i = 0; i < rowCount; i++) {
        idVec.setSafe(i, i);
        nameVec.setSafe(i, ("row-" + i).getBytes(StandardCharsets.UTF_8));
        vecVec.setNotNull(i);
        for (int d = 0; d < VECTOR_DIM; d++) {
          vecChild.setSafe(i * VECTOR_DIM + d, (float) (i + d * 0.1));
        }
      }
      idVec.setValueCount(rowCount);
      nameVec.setValueCount(rowCount);
      vecChild.setValueCount(rowCount * VECTOR_DIM);
      vecVec.setValueCount(rowCount);
      root.setRowCount(rowCount);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (ArrowStreamWriter writer = new ArrowStreamWriter(root, null, out)) {
        writer.start();
        writer.writeBatch();
        writer.end();
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to build Arrow IPC stream", e);
    }
  }
}
