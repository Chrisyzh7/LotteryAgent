package com.yzh.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Lightweight runtime schema compatibility for local environments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaCompatibilityInitializer {

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureSchemaCompatibility() {
        try (Connection conn = dataSource.getConnection()) {
            String schema = currentSchema(conn);
            if (schema == null || schema.isBlank()) {
                log.warn("[SchemaCompatibility] schema not found, skip compatibility check");
                return;
            }

            ensureColumn(
                    conn,
                    schema,
                    "marketing_activity",
                    "page_style",
                    "ALTER TABLE `marketing_activity` " +
                            "ADD COLUMN `page_style` varchar(32) DEFAULT 'dark_neon' " +
                            "COMMENT 'page style: dark_neon/ins_minimal/fresh_light' AFTER `deduct_points`"
            );

            ensureColumn(
                    conn,
                    schema,
                    "marketing_activity",
                    "initial_user_points",
                    "ALTER TABLE `marketing_activity` " +
                            "ADD COLUMN `initial_user_points` decimal(10,2) NOT NULL DEFAULT '100.00' " +
                            "COMMENT 'initial points for activity users' AFTER `deduct_points`"
            );

            ensureTable(
                    conn,
                    schema,
                    "c_user",
                    "CREATE TABLE IF NOT EXISTS `c_user` (" +
                            "`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'pk'," +
                            "`c_user_id` varchar(64) NOT NULL COMMENT 'c-side unique id'," +
                            "`username` varchar(64) DEFAULT NULL COMMENT 'login username'," +
                            "`password_hash` varchar(255) DEFAULT NULL COMMENT 'password hash'," +
                            "`nickname` varchar(128) DEFAULT NULL COMMENT 'nickname'," +
                            "`mobile` varchar(32) DEFAULT NULL COMMENT 'mobile'," +
                            "`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created at'," +
                            "`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at'," +
                            "PRIMARY KEY (`id`)," +
                            "UNIQUE KEY `uk_c_user_id` (`c_user_id`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='c-side users'"
            );

            ensureColumn(
                    conn,
                    schema,
                    "c_user",
                    "username",
                    "ALTER TABLE `c_user` ADD COLUMN `username` varchar(64) DEFAULT NULL COMMENT 'login username' AFTER `c_user_id`"
            );

            ensureColumn(
                    conn,
                    schema,
                    "c_user",
                    "password_hash",
                    "ALTER TABLE `c_user` ADD COLUMN `password_hash` varchar(255) DEFAULT NULL COMMENT 'password hash' AFTER `username`"
            );

            ensureIndex(
                    conn,
                    schema,
                    "c_user",
                    "uk_username",
                    "ALTER TABLE `c_user` ADD UNIQUE KEY `uk_username` (`username`)"
            );

            ensureTable(
                    conn,
                    schema,
                    "c_user_points",
                    "CREATE TABLE IF NOT EXISTS `c_user_points` (" +
                            "`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'pk'," +
                            "`activity_id` bigint(20) NOT NULL COMMENT 'activity id'," +
                            "`c_user_id` varchar(64) NOT NULL COMMENT 'c-side user id'," +
                            "`total_points` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'total granted points'," +
                            "`used_points` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'total consumed points'," +
                            "`remain_points` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'remaining points'," +
                            "`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created at'," +
                            "`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at'," +
                            "PRIMARY KEY (`id`)," +
                            "UNIQUE KEY `uk_activity_user` (`activity_id`,`c_user_id`)," +
                            "KEY `idx_c_user_id` (`c_user_id`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='c-side activity points account'"
            );
        } catch (Exception e) {
            log.error("[SchemaCompatibility] compatibility check failed: {}", e.getMessage(), e);
        }
    }

    private String currentSchema(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT DATABASE()");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        }
    }

    private void ensureTable(Connection conn, String schema, String tableName, String createDdl) throws Exception {
        String sql = "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema=? AND table_name=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    return;
                }
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute(createDdl);
            log.info("[SchemaCompatibility] auto created table: {}", tableName);
        }
    }

    private void ensureColumn(Connection conn, String schema, String tableName, String columnName, String alterDdl) throws Exception {
        String sql = "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema=? AND table_name=? AND column_name=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            ps.setString(3, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    return;
                }
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute(alterDdl);
            log.info("[SchemaCompatibility] auto added column: {}.{}", tableName, columnName);
        }
    }

    private void ensureIndex(Connection conn, String schema, String tableName, String indexName, String alterDdl) throws Exception {
        String sql = "SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema=? AND table_name=? AND index_name=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            ps.setString(3, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    return;
                }
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute(alterDdl);
            log.info("[SchemaCompatibility] auto added index: {}.{}", tableName, indexName);
        }
    }
}
