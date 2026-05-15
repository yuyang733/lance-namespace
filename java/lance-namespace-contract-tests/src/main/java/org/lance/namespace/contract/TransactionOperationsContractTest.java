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
import org.lance.namespace.model.AlterTransactionAction;
import org.lance.namespace.model.AlterTransactionRequest;
import org.lance.namespace.model.DescribeTransactionRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.lance.namespace.contract.ContractAssertions.assertThrowsWithCode;

/**
 * Contract tests for transaction operations.
 *
 * <p>Transaction lifecycle (start/commit/rollback) is backend specific and the public spec only
 * defines two operations on already-known transaction handles: {@code describeTransaction} and
 * {@code alterTransaction}. This base verifies the spec-required negative paths
 * (operating on a non-existent transaction must yield {@link ErrorCode#TRANSACTION_NOT_FOUND}).
 *
 * <p>Backends that support a richer transaction surface should extend this base with their own
 * happy-path tests using their own {@code beginTransaction}-equivalent fixture.
 */
public abstract class TransactionOperationsContractTest
    extends AbstractLanceNamespaceContractTest {

  /** Identifier of a transaction that the backend guarantees does NOT exist. */
  protected java.util.List<String> nonExistentTransactionId() {
    return id("__contract_test_ghost_txn__");
  }

  @Nested
  @DisplayName("describeTransaction")
  class DescribeTransaction {

    @Test
    @DisplayName("on a missing transaction raises TRANSACTION_NOT_FOUND")
    void missingTxnRaises() {
      requires(Capability.TXN_DESCRIBE);

      assertThrowsWithCode(
          "describeTransaction(missing)",
          () ->
              namespace.describeTransaction(
                  new DescribeTransactionRequest().id(nonExistentTransactionId())),
          ErrorCode.TRANSACTION_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("alterTransaction")
  class AlterTransaction {

    @Test
    @DisplayName("on a missing transaction raises TRANSACTION_NOT_FOUND")
    void missingTxnRaises() {
      requires(Capability.TXN_ALTER);

      assertThrowsWithCode(
          "alterTransaction(missing)",
          () ->
              namespace.alterTransaction(
                  new AlterTransactionRequest()
                      .id(nonExistentTransactionId())
                      .actions(Collections.<AlterTransactionAction>emptyList())),
          ErrorCode.TRANSACTION_NOT_FOUND);
    }
  }
}
