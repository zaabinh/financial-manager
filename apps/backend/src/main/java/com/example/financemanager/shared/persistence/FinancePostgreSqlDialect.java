package com.example.financemanager.shared.persistence;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

import java.util.Locale;

public class FinancePostgreSqlDialect extends PostgreSQLDialect {

    @Override
    public JdbcType resolveSqlTypeDescriptor(
            String columnTypeName,
            int jdbcTypeCode,
            int precision,
            int scale,
            JdbcTypeRegistry jdbcTypeRegistry
    ) {
        int resolvedTypeCode = jdbcTypeCode;
        if (isCitext(columnTypeName) && jdbcTypeCode == SqlTypes.OTHER) {
            resolvedTypeCode = SqlTypes.VARCHAR;
        }

        return super.resolveSqlTypeDescriptor(
                columnTypeName,
                resolvedTypeCode,
                precision,
                scale,
                jdbcTypeRegistry
        );
    }

    private boolean isCitext(String columnTypeName) {
        if (columnTypeName == null) {
            return false;
        }

        String normalizedTypeName = columnTypeName
                .replace("\"", "")
                .toLowerCase(Locale.ROOT);
        return normalizedTypeName.equals("citext") || normalizedTypeName.endsWith(".citext");
    }
}
