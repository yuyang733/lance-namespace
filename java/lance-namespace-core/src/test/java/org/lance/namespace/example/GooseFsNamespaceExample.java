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
package org.lance.namespace.example;

import org.lance.namespace.LanceNamespace;
import org.lance.namespace.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * GooseFS Namespace 使用示例。
 *
 * <p>本示例展示如何连接 GooseFS 实现，并调用 {@link LanceNamespace} 接口的所有方法。 使用前请确保已注册 GooseFS 实现，并将 GooseFS 实现的
 * JAR 放入 classpath 中。
 */
public class GooseFsNamespaceExample {

  /** 命名空间 ID，用于标识 namespace 层级 */
  private static final List<String> NAMESPACE_ID = Arrays.asList("my_database");

  /** 表 ID，格式为 [namespace..., tableName] */
  private static final List<String> TABLE_ID = Arrays.asList("my_database", "my_table");

  public static void main(String[] args) {
    // ============================
    // 1. 连接 GooseFS Namespace
    // ============================
    LanceNamespace namespace =
        LanceNamespace.connect(
            "goosefs",
            Map.of(
                "uri", "goosefs://master:9220",
                "root", "/data/lance"),
            null);

    System.out.println("已连接命名空间: " + namespace.namespaceId());

    // ============================
    // 2. 命名空间 (Namespace) 操作
    // ============================

    // 2.1 创建命名空间
    CreateNamespaceResponse createNsResp =
        namespace.createNamespace(
            new CreateNamespaceRequest()
                .id(NAMESPACE_ID)
                .mode("Create")
                .putPropertiesItem("description", "示例数据库"));
    System.out.println("创建命名空间: " + createNsResp);

    // 2.2 列出命名空间
    ListNamespacesResponse listNsResp = namespace.listNamespaces(new ListNamespacesRequest());
    System.out.println("命名空间列表: " + listNsResp);

    // 2.3 描述命名空间
    DescribeNamespaceResponse descNsResp =
        namespace.describeNamespace(new DescribeNamespaceRequest().id(NAMESPACE_ID));
    System.out.println("命名空间详情: " + descNsResp);

    // 2.4 检查命名空间是否存在
    namespace.namespaceExists(new NamespaceExistsRequest().id(NAMESPACE_ID));
    System.out.println("命名空间存在检查通过");

    // ============================
    // 3. 表 (Table) 基础操作
    // ============================

    // 3.1 使用 Arrow IPC 数据创建表
    byte[] arrowIpcData = new byte[0]; // 实际使用时请提供真实的 Arrow IPC 流数据
    CreateTableResponse createTableResp =
        namespace.createTable(new CreateTableRequest().id(TABLE_ID).mode("Create"), arrowIpcData);
    System.out.println("创建表: " + createTableResp);

    // 3.2 声明表（仅元数据操作，不含实际数据）
    DeclareTableResponse declareTableResp =
        namespace.declareTable(
            new DeclareTableRequest()
                .id(Arrays.asList("my_database", "declared_table"))
                .location("goosefs:///data/lance/declared_table"));
    System.out.println("声明表: " + declareTableResp);

    // 3.3 创建空表（已弃用，推荐使用 declareTable）
    @SuppressWarnings("deprecation")
    CreateEmptyTableResponse createEmptyResp =
        namespace.createEmptyTable(
            new CreateEmptyTableRequest().id(Arrays.asList("my_database", "empty_table")));
    System.out.println("创建空表: " + createEmptyResp);

    // 3.4 注册表
    RegisterTableResponse registerResp =
        namespace.registerTable(
            new RegisterTableRequest().id(Arrays.asList("my_database", "registered_table")));
    System.out.println("注册表: " + registerResp);

    // 3.5 列出指定命名空间下的表
    ListTablesResponse listTablesResp =
        namespace.listTables(new ListTablesRequest().id(NAMESPACE_ID));
    System.out.println("表列表: " + listTablesResp);

    // 3.6 列出所有命名空间下的表
    ListTablesResponse listAllTablesResp = namespace.listAllTables(new ListTablesRequest());
    System.out.println("所有表列表: " + listAllTablesResp);

    // 3.7 描述表
    DescribeTableResponse descTableResp =
        namespace.describeTable(
            new DescribeTableRequest()
                .id(TABLE_ID)
                .withTableUri(true)
                .loadDetailedMetadata(true)
                .vendCredentials(false));
    System.out.println("表详情: " + descTableResp);

    // 3.8 检查表是否存在
    namespace.tableExists(new TableExistsRequest().id(TABLE_ID));
    System.out.println("表存在检查通过");

    // 3.9 统计表行数
    Long rowCount = namespace.countTableRows(new CountTableRowsRequest().id(TABLE_ID));
    System.out.println("表行数: " + rowCount);

    // 3.10 获取表统计信息
    GetTableStatsResponse statsResp =
        namespace.getTableStats(new GetTableStatsRequest().id(TABLE_ID));
    System.out.println("表统计信息: " + statsResp);

    // 3.11 重命名表
    RenameTableResponse renameResp =
        namespace.renameTable(
            new RenameTableRequest().id(TABLE_ID).newTableName("my_table_renamed"));
    System.out.println("重命名表: " + renameResp);

    // ============================
    // 4. 数据 (Data) 操作
    // ============================

    // 4.1 插入数据
    byte[] insertData = new byte[0]; // 实际使用时请提供真实的 Arrow IPC 流数据
    InsertIntoTableResponse insertResp =
        namespace.insertIntoTable(new InsertIntoTableRequest().id(TABLE_ID), insertData);
    System.out.println("插入数据: " + insertResp);

    // 4.2 合并插入数据 (Merge Insert / Upsert)
    byte[] mergeData = new byte[0]; // 实际使用时请提供真实的 Arrow IPC 流数据
    MergeInsertIntoTableResponse mergeResp =
        namespace.mergeInsertIntoTable(
            new MergeInsertIntoTableRequest()
                .id(TABLE_ID)
                .on("id")
                .whenMatchedUpdateAll(true)
                .whenNotMatchedInsertAll(true),
            mergeData);
    System.out.println("合并插入: " + mergeResp);

    // 4.3 更新表数据
    UpdateTableResponse updateResp =
        namespace.updateTable(
            new UpdateTableRequest()
                .id(TABLE_ID)
                .predicate("age > 30")
                .addUpdatesItem(Arrays.asList("status", "'senior'")));
    System.out.println("更新数据: " + updateResp);

    // 4.4 删除表数据
    DeleteFromTableResponse deleteResp =
        namespace.deleteFromTable(
            new DeleteFromTableRequest().id(TABLE_ID).predicate("status = 'inactive'"));
    System.out.println("删除数据: " + deleteResp);

    // 4.5 查询表数据
    byte[] queryResult =
        namespace.queryTable(new QueryTableRequest().id(TABLE_ID).filter("age >= 18").k(10));
    System.out.println("查询结果 (Arrow IPC 字节数): " + queryResult.length);

    // ============================
    // 5. 索引 (Index) 操作
    // ============================

    // 5.1 创建向量索引
    CreateTableIndexResponse createIdxResp =
        namespace.createTableIndex(
            new CreateTableIndexRequest()
                .id(TABLE_ID)
                .column("vector")
                .indexType("IVF_PQ")
                .name("vector_idx")
                .distanceType("L2"));
    System.out.println("创建向量索引: " + createIdxResp);

    // 5.2 创建标量索引
    CreateTableScalarIndexResponse scalarIdxResp =
        namespace.createTableScalarIndex(
            new CreateTableIndexRequest()
                .id(TABLE_ID)
                .column("name")
                .indexType("BTREE")
                .name("name_idx"));
    System.out.println("创建标量索引: " + scalarIdxResp);

    // 5.3 列出索引
    ListTableIndicesResponse listIdxResp =
        namespace.listTableIndices(new ListTableIndicesRequest().id(TABLE_ID));
    System.out.println("索引列表: " + listIdxResp);

    // 5.4 描述索引统计信息
    DescribeTableIndexStatsResponse idxStatsResp =
        namespace.describeTableIndexStats(
            new DescribeTableIndexStatsRequest().id(TABLE_ID), "vector_idx");
    System.out.println("索引统计: " + idxStatsResp);

    // 5.5 删除索引
    DropTableIndexResponse dropIdxResp =
        namespace.dropTableIndex(new DropTableIndexRequest().id(TABLE_ID), "vector_idx");
    System.out.println("删除索引: " + dropIdxResp);

    // ============================
    // 6. 版本 (Version) 操作
    // ============================

    // 6.1 创建表版本
    CreateTableVersionResponse createVerResp =
        namespace.createTableVersion(
            new CreateTableVersionRequest()
                .id(TABLE_ID)
                .version(2L)
                .manifestPath("/data/lance/my_table/_versions/2.manifest"));
    System.out.println("创建版本: " + createVerResp);

    // 6.2 列出表版本
    ListTableVersionsResponse listVerResp =
        namespace.listTableVersions(new ListTableVersionsRequest().id(TABLE_ID));
    System.out.println("版本列表: " + listVerResp);

    // 6.3 描述特定表版本
    DescribeTableVersionResponse descVerResp =
        namespace.describeTableVersion(new DescribeTableVersionRequest().id(TABLE_ID).version(1L));
    System.out.println("版本详情: " + descVerResp);

    // 6.4 批量创建表版本
    BatchCreateTableVersionsResponse batchCreateVerResp =
        namespace.batchCreateTableVersions(
            new BatchCreateTableVersionsRequest()
                .addEntriesItem(
                    new CreateTableVersionEntry()
                        .id(TABLE_ID)
                        .version(3L)
                        .manifestPath("/data/lance/my_table/_versions/3.manifest")));
    System.out.println("批量创建版本: " + batchCreateVerResp);

    // 6.5 批量删除表版本
    BatchDeleteTableVersionsResponse batchDelVerResp =
        namespace.batchDeleteTableVersions(
            new BatchDeleteTableVersionsRequest()
                .id(TABLE_ID)
                .addRangesItem(new VersionRange().startVersion(1L).endVersion(3L)));
    System.out.println("批量删除版本: " + batchDelVerResp);

    // 6.6 恢复表到指定版本
    RestoreTableResponse restoreResp =
        namespace.restoreTable(new RestoreTableRequest().id(TABLE_ID).version(1L));
    System.out.println("恢复版本: " + restoreResp);

    // ============================
    // 7. Schema 操作
    // ============================

    // 7.1 更新表 Schema 元数据
    UpdateTableSchemaMetadataResponse updateSchemaResp =
        namespace.updateTableSchemaMetadata(
            new UpdateTableSchemaMetadataRequest()
                .id(TABLE_ID)
                .putMetadataItem("author", "lance-team"));
    System.out.println("更新 Schema 元数据: " + updateSchemaResp);

    // ============================
    // 8. 列 (Column) 操作
    // ============================

    // 8.1 添加列
    AlterTableAddColumnsResponse addColResp =
        namespace.alterTableAddColumns(
            new AlterTableAddColumnsRequest()
                .id(TABLE_ID)
                .addNewColumnsItem(new NewColumnTransform().name("new_column").expression("0")));
    System.out.println("添加列: " + addColResp);

    // 8.2 修改列
    AlterTableAlterColumnsResponse alterColResp =
        namespace.alterTableAlterColumns(
            new AlterTableAlterColumnsRequest()
                .id(TABLE_ID)
                .addAlterationsItem(
                    new AlterColumnsEntry()
                        .path("new_column")
                        .dataType("string")
                        .rename("renamed_column")));
    System.out.println("修改列: " + alterColResp);

    // 8.3 删除列
    AlterTableDropColumnsResponse dropColResp =
        namespace.alterTableDropColumns(
            new AlterTableDropColumnsRequest().id(TABLE_ID).addColumnsItem("renamed_column"));
    System.out.println("删除列: " + dropColResp);

    // ============================
    // 9. 标签 (Tag) 操作
    // ============================

    // 9.1 创建标签
    CreateTableTagResponse createTagResp =
        namespace.createTableTag(new CreateTableTagRequest().id(TABLE_ID).tag("v1.0").version(1L));
    System.out.println("创建标签: " + createTagResp);

    // 9.2 列出标签
    ListTableTagsResponse listTagsResp =
        namespace.listTableTags(new ListTableTagsRequest().id(TABLE_ID));
    System.out.println("标签列表: " + listTagsResp);

    // 9.3 获取标签对应的版本
    GetTableTagVersionResponse tagVerResp =
        namespace.getTableTagVersion(new GetTableTagVersionRequest().id(TABLE_ID).tag("v1.0"));
    System.out.println("标签版本: " + tagVerResp);

    // 9.4 更新标签
    UpdateTableTagResponse updateTagResp =
        namespace.updateTableTag(new UpdateTableTagRequest().id(TABLE_ID).tag("v1.0").version(2L));
    System.out.println("更新标签: " + updateTagResp);

    // 9.5 删除标签
    DeleteTableTagResponse delTagResp =
        namespace.deleteTableTag(new DeleteTableTagRequest().id(TABLE_ID).tag("v1.0"));
    System.out.println("删除标签: " + delTagResp);

    // ============================
    // 10. 查询计划 (Query Plan) 操作
    // ============================

    // 10.1 解释查询计划
    String explainResult =
        namespace.explainTableQueryPlan(new ExplainTableQueryPlanRequest().id(TABLE_ID));
    System.out.println("查询计划: " + explainResult);

    // 10.2 分析查询计划
    String analyzeResult =
        namespace.analyzeTableQueryPlan(new AnalyzeTableQueryPlanRequest().id(TABLE_ID));
    System.out.println("查询分析: " + analyzeResult);

    // ============================
    // 11. 事务 (Transaction) 操作
    // ============================

    // 11.1 描述事务
    DescribeTransactionResponse descTxnResp =
        namespace.describeTransaction(new DescribeTransactionRequest().id(TABLE_ID));
    System.out.println("事务详情: " + descTxnResp);

    // 11.2 修改事务
    AlterTransactionResponse alterTxnResp =
        namespace.alterTransaction(new AlterTransactionRequest().id(TABLE_ID));
    System.out.println("修改事务: " + alterTxnResp);

    // ============================
    // 12. 清理操作
    // ============================

    // 12.1 反注册表
    DeregisterTableResponse deregResp =
        namespace.deregisterTable(new DeregisterTableRequest().id(TABLE_ID));
    System.out.println("反注册表: " + deregResp);

    // 12.2 删除表
    DropTableResponse dropTableResp = namespace.dropTable(new DropTableRequest().id(TABLE_ID));
    System.out.println("删除表: " + dropTableResp);

    // 12.3 删除命名空间
    DropNamespaceResponse dropNsResp =
        namespace.dropNamespace(new DropNamespaceRequest().id(NAMESPACE_ID));
    System.out.println("删除命名空间: " + dropNsResp);

    System.out.println("\n===== GooseFS Namespace 全接口调用示例完成 =====");
  }
}
