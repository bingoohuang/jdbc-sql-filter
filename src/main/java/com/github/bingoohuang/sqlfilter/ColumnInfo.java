package com.github.bingoohuang.sqlfilter;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data @RequiredArgsConstructor
public class ColumnInfo {
    private final String name;
    private int varIndex;
    private ValueType valueType;
    private Object value;
}
