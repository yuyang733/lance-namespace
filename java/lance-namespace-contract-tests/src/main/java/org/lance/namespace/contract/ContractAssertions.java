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
import org.lance.namespace.errors.LanceNamespaceException;

import org.junit.jupiter.api.function.Executable;
import org.opentest4j.AssertionFailedError;

import java.util.Arrays;
import java.util.Objects;

/**
 * Reusable assertion helpers shared across all Lance Namespace contract test bases.
 *
 * <p>These wrappers add Lance-specific context to JUnit 5 assertion failures, e.g. printing the
 * expected {@link ErrorCode} alongside the actual one to make spec-conformance regressions easy
 * to triage.
 */
public final class ContractAssertions {

  private ContractAssertions() {}

  /**
   * Assert that {@code executable} throws a {@link LanceNamespaceException} whose
   * {@link LanceNamespaceException#getErrorCode()} is one of {@code expected}.
   *
   * @param description human-readable description of the operation under test (used in failure
   *     messages)
   * @param executable the operation that should fail
   * @param expected acceptable error codes; the test passes if the thrown exception carries any
   *     of these
   * @return the captured exception (for further fine-grained inspection)
   */
  public static LanceNamespaceException assertThrowsWithCode(
      String description, Executable executable, ErrorCode... expected) {
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(executable, "executable");
    if (expected == null || expected.length == 0) {
      throw new IllegalArgumentException("at least one expected ErrorCode must be supplied");
    }

    Throwable thrown;
    try {
      executable.execute();
      throw new AssertionFailedError(
          "[" + description + "] expected LanceNamespaceException with one of "
              + Arrays.toString(expected) + " but no exception was thrown");
    } catch (Throwable t) {
      thrown = t;
    }

    if (!(thrown instanceof LanceNamespaceException)) {
      throw new AssertionFailedError(
          "[" + description + "] expected LanceNamespaceException with one of "
              + Arrays.toString(expected) + " but got " + thrown.getClass().getName()
              + ": " + thrown.getMessage(),
          thrown);
    }
    LanceNamespaceException e = (LanceNamespaceException) thrown;
    for (ErrorCode candidate : expected) {
      if (candidate == e.getErrorCode()) {
        return e;
      }
    }
    throw new AssertionFailedError(
        "[" + description + "] expected error code in " + Arrays.toString(expected)
            + " but got " + e.getErrorCode() + " (message: " + e.getMessage() + ")",
        e);
  }

  /** Assert {@code value} is non-null and not blank, raising a context-rich AssertionError. */
  public static void assertNotBlank(String holder, String fieldName, String value) {
    if (value == null || value.isEmpty()) {
      throw new AssertionFailedError(
          holder + "." + fieldName + " must not be null/empty per Lance Namespace spec");
    }
  }

  /** Assert that {@code version} is a valid table-version number (non-null, &gt;= 1). */
  public static void assertValidVersion(String operation, Long version) {
    if (version == null) {
      throw new AssertionFailedError(
          operation + " response must carry a non-null 'version' field");
    }
    if (version < 1L) {
      throw new AssertionFailedError(
          operation + " returned version=" + version + " but spec requires version >= 1");
    }
  }

  /** Assert that {@code newer} version is strictly &gt; {@code older}. */
  public static void assertVersionMonotonic(long older, long newer) {
    if (newer <= older) {
      throw new AssertionFailedError(
          "version must be strictly increasing: previous=" + older + ", newer=" + newer);
    }
  }

  /** Assert that {@code newer} version is strictly &gt; {@code older} (boxed convenience). */
  public static void assertVersionMonotonic(Long older, Long newer) {
    if (older == null || newer == null) {
      throw new AssertionFailedError(
          "expected non-null versions, got older=" + older + ", newer=" + newer);
    }
    assertVersionMonotonic(older.longValue(), newer.longValue());
  }

  /**
   * Returns true when {@code pageToken} represents the end of a paginated stream. The Lance
   * Namespace spec defines the terminal token as {@code null} or empty string.
   */
  public static boolean isPageTokenTerminal(String pageToken) {
    return pageToken == null || pageToken.isEmpty();
  }
}
