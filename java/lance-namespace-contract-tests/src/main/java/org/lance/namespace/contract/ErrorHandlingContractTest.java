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

import org.lance.namespace.errors.ConcurrentModificationException;
import org.lance.namespace.errors.ErrorCode;
import org.lance.namespace.errors.InternalException;
import org.lance.namespace.errors.InvalidInputException;
import org.lance.namespace.errors.LanceNamespaceException;
import org.lance.namespace.errors.NamespaceAlreadyExistsException;
import org.lance.namespace.errors.NamespaceNotEmptyException;
import org.lance.namespace.errors.NamespaceNotFoundException;
import org.lance.namespace.errors.PermissionDeniedException;
import org.lance.namespace.errors.ServiceUnavailableException;
import org.lance.namespace.errors.TableAlreadyExistsException;
import org.lance.namespace.errors.TableColumnNotFoundException;
import org.lance.namespace.errors.TableIndexAlreadyExistsException;
import org.lance.namespace.errors.TableIndexNotFoundException;
import org.lance.namespace.errors.TableNotFoundException;
import org.lance.namespace.errors.TableSchemaValidationException;
import org.lance.namespace.errors.TableTagAlreadyExistsException;
import org.lance.namespace.errors.TableTagNotFoundException;
import org.lance.namespace.errors.TableVersionNotFoundException;
import org.lance.namespace.errors.ThrottlingException;
import org.lance.namespace.errors.TransactionNotFoundException;
import org.lance.namespace.errors.UnauthenticatedException;
import org.lance.namespace.errors.UnsupportedOperationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the error model itself - {@link ErrorCode} pinning, exception class -&gt;
 * code mapping, and serialization-relevant helpers.
 *
 * <p>This test does <em>not</em> exercise a backend at all; it locks down the contract that every
 * backend (and every spec consumer) relies on:
 *
 * <ul>
 *   <li>Each {@link ErrorCode} has a stable numeric code (changing one is a breaking change).
 *   <li>Each typed exception in {@code org.lance.namespace.errors} carries the matching
 *       {@link ErrorCode}.
 *   <li>{@link LanceNamespaceException#getCode()} reports the underlying numeric code.
 *   <li>{@link ErrorCode#fromCode(int)} round-trips with {@link ErrorCode#getCode()}.
 * </ul>
 *
 * <p>Because the test is backend-agnostic it is a concrete (not abstract) class. Subclasses are
 * not required, but it is included in {@code FullContractSuite} so it is exercised whenever a
 * backend runs the suite.
 */
public class ErrorHandlingContractTest {

  @Test
  @DisplayName("ErrorCode numeric codes are stable")
  void errorCodesArePinned() {
    // Pinning these values protects on-the-wire compatibility for clients that key off the int.
    assertEquals(0, ErrorCode.UNSUPPORTED.getCode());
    assertEquals(1, ErrorCode.NAMESPACE_NOT_FOUND.getCode());
    assertEquals(2, ErrorCode.NAMESPACE_ALREADY_EXISTS.getCode());
    assertEquals(3, ErrorCode.NAMESPACE_NOT_EMPTY.getCode());
    assertEquals(4, ErrorCode.TABLE_NOT_FOUND.getCode());
    assertEquals(5, ErrorCode.TABLE_ALREADY_EXISTS.getCode());
    assertEquals(6, ErrorCode.TABLE_INDEX_NOT_FOUND.getCode());
    assertEquals(7, ErrorCode.TABLE_INDEX_ALREADY_EXISTS.getCode());
    assertEquals(8, ErrorCode.TABLE_TAG_NOT_FOUND.getCode());
    assertEquals(9, ErrorCode.TABLE_TAG_ALREADY_EXISTS.getCode());
    assertEquals(10, ErrorCode.TRANSACTION_NOT_FOUND.getCode());
    assertEquals(11, ErrorCode.TABLE_VERSION_NOT_FOUND.getCode());
    assertEquals(12, ErrorCode.TABLE_COLUMN_NOT_FOUND.getCode());
    assertEquals(13, ErrorCode.INVALID_INPUT.getCode());
    assertEquals(14, ErrorCode.CONCURRENT_MODIFICATION.getCode());
    assertEquals(15, ErrorCode.PERMISSION_DENIED.getCode());
    assertEquals(16, ErrorCode.UNAUTHENTICATED.getCode());
    assertEquals(17, ErrorCode.SERVICE_UNAVAILABLE.getCode());
    assertEquals(18, ErrorCode.INTERNAL.getCode());
    assertEquals(19, ErrorCode.INVALID_TABLE_STATE.getCode());
    assertEquals(20, ErrorCode.TABLE_SCHEMA_VALIDATION_ERROR.getCode());
    assertEquals(21, ErrorCode.THROTTLING.getCode());
  }

  @Test
  @DisplayName("ErrorCode descriptions are non-blank")
  void descriptionsArePresent() {
    for (ErrorCode code : ErrorCode.values()) {
      String desc = code.getDescription();
      assertNotNull(desc, code + ".description must not be null");
      assertTrue(!desc.isEmpty(), code + ".description must not be empty");
    }
  }

  @Test
  @DisplayName("ErrorCode#fromCode round-trips with getCode")
  void fromCodeRoundTrips() {
    for (ErrorCode code : ErrorCode.values()) {
      assertSame(
          code,
          ErrorCode.fromCode(code.getCode()),
          "fromCode(" + code.getCode() + ") must return " + code);
    }
  }

  @Test
  @DisplayName("ErrorCode#fromCode falls back to INTERNAL for unknown ids")
  void fromCodeUnknownDefaults() {
    assertSame(ErrorCode.INTERNAL, ErrorCode.fromCode(-1));
    assertSame(ErrorCode.INTERNAL, ErrorCode.fromCode(99999));
  }

  @Test
  @DisplayName("Each typed exception carries the matching ErrorCode")
  void typedExceptionsCarryMatchingCode() {
    assertExceptionCarries(
        new NamespaceNotFoundException("x"), ErrorCode.NAMESPACE_NOT_FOUND);
    assertExceptionCarries(
        new NamespaceAlreadyExistsException("x"), ErrorCode.NAMESPACE_ALREADY_EXISTS);
    assertExceptionCarries(new NamespaceNotEmptyException("x"), ErrorCode.NAMESPACE_NOT_EMPTY);
    assertExceptionCarries(new TableNotFoundException("x"), ErrorCode.TABLE_NOT_FOUND);
    assertExceptionCarries(new TableAlreadyExistsException("x"), ErrorCode.TABLE_ALREADY_EXISTS);
    assertExceptionCarries(new TableIndexNotFoundException("x"), ErrorCode.TABLE_INDEX_NOT_FOUND);
    assertExceptionCarries(
        new TableIndexAlreadyExistsException("x"), ErrorCode.TABLE_INDEX_ALREADY_EXISTS);
    assertExceptionCarries(new TableTagNotFoundException("x"), ErrorCode.TABLE_TAG_NOT_FOUND);
    assertExceptionCarries(
        new TableTagAlreadyExistsException("x"), ErrorCode.TABLE_TAG_ALREADY_EXISTS);
    assertExceptionCarries(new TransactionNotFoundException("x"), ErrorCode.TRANSACTION_NOT_FOUND);
    assertExceptionCarries(
        new TableVersionNotFoundException("x"), ErrorCode.TABLE_VERSION_NOT_FOUND);
    assertExceptionCarries(
        new TableColumnNotFoundException("x"), ErrorCode.TABLE_COLUMN_NOT_FOUND);
    assertExceptionCarries(new InvalidInputException("x"), ErrorCode.INVALID_INPUT);
    assertExceptionCarries(
        new ConcurrentModificationException("x"), ErrorCode.CONCURRENT_MODIFICATION);
    assertExceptionCarries(new PermissionDeniedException("x"), ErrorCode.PERMISSION_DENIED);
    assertExceptionCarries(new UnauthenticatedException("x"), ErrorCode.UNAUTHENTICATED);
    assertExceptionCarries(new ServiceUnavailableException("x"), ErrorCode.SERVICE_UNAVAILABLE);
    assertExceptionCarries(new InternalException("x"), ErrorCode.INTERNAL);
    assertExceptionCarries(
        new TableSchemaValidationException("x"), ErrorCode.TABLE_SCHEMA_VALIDATION_ERROR);
    assertExceptionCarries(new ThrottlingException("x"), ErrorCode.THROTTLING);
    assertExceptionCarries(new UnsupportedOperationException("x"), ErrorCode.UNSUPPORTED);
  }

  private static void assertExceptionCarries(LanceNamespaceException ex, ErrorCode expected) {
    assertSame(
        expected,
        ex.getErrorCode(),
        ex.getClass().getSimpleName() + " must report ErrorCode=" + expected);
    assertEquals(
        expected.getCode(),
        ex.getCode(),
        ex.getClass().getSimpleName() + ".getCode() must match ErrorCode#getCode");
  }
}
