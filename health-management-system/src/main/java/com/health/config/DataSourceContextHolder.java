package com.health.config;

public final class DataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT = ThreadLocal.withInitial(() -> DataSourceType.MASTER);

    private DataSourceContextHolder() {
    }

    public static void setDataSourceType(DataSourceType dataSourceType) {
        CONTEXT.set(dataSourceType == null ? DataSourceType.MASTER : dataSourceType);
    }

    public static DataSourceType getDataSourceType() {
        return CONTEXT.get();
    }

    public static void clearDataSourceType() {
        CONTEXT.remove();
    }
}
