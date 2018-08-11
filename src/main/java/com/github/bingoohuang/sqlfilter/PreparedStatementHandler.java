package com.github.bingoohuang.sqlfilter;

import com.github.bingoohuang.utils.lang.Str;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.joor.Reflect;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.bingoohuang.sqlfilter.ReflectUtil.invokeMethod;
import static com.github.bingoohuang.sqlfilter.ReflectUtil.setField;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@RequiredArgsConstructor
public class PreparedStatementHandler implements InvocationHandler {
    private final Object preparedStatement;
    private final List<FilterItem> items;
    private final List<Map<Integer, ColumnInfo>> colsList;
    private final Map<Integer, ColumnInfo> setCols;
    private final Object filter;

    Map<Integer, Object> parameters = null;


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (Str.anyOf(method.getName(), "setString", "setObject")) {
            int parameterIndex = (Integer) args[0];
            if (parameters == null)
                parameters = Maps.newHashMap();
            parameters.put(parameterIndex, args[1]);
        } else if (method.getName().equals("executeUpdate")) {
            invokeFilter();
            parameters = null;
        }

        return invokeMethod(method, preparedStatement, args);
    }


    private void invokeFilter() {
        for (val item : items) {
            invokeFilter(item);
        }
    }

    private void invokeFilter(FilterItem item) {
        for (val cols : colsList) {
            invokeFilter(cols, item);
        }
    }

    private void invokeFilter(Map<Integer, ColumnInfo> cols, FilterItem item) {
        List<Object> args = Lists.newArrayList();

        int beanIndex = 0;
        for (val parameter : item.getMethod().getParameters()) {
            if (parameter.getType() == SqlFilterContext.class) {
                args.add(new SqlFilterContext());
            } else {
                if (beanIndex == 0) {
                    args.add(createBean(parameter, cols));
                    beanIndex = 1;
                } else if (beanIndex == 1 && setCols != null) {
                    args.add(createBean(parameter, setCols));
                    beanIndex = 2;
                }
            }
        }

        invokeMethod(item.getMethod(), filter, args.toArray(new Object[0]));
    }


    private Object createBean(Parameter parameter, Map<Integer, ColumnInfo> cols) {
        Class<?> parameterType = parameter.getType();
        val param = Reflect.on(parameterType).create().get();

        int mapped = 0;
        for (val field : parameterType.getDeclaredFields()) {
            val columnInfo = findColumn(cols, createAllowedNames(field));
            if (columnInfo == null) continue;

            val valueType = columnInfo.getValueType();
            if (valueType == ValueType.VariantRef) {
                setField(field, param, parameters.get(columnInfo.getVarIndex()));
            } else if (valueType == ValueType.Literal) {
                setField(field, param, columnInfo.getValue());
            }

            setMapped(parameterType, param, field);
            ++mapped;
        }

        setNoneMapped(parameterType, param, mapped == 0);

        return param;
    }

    private void setNoneMapped(Class<?> parameterType, Object param, boolean noneMapped) {
        val mappedField = ReflectUtil.findField(parameterType, "noneMapped");
        if (mappedField == null) return;

        setField(mappedField, param, noneMapped);
    }

    private void setMapped(Class<?> parameterType, Object param, Field field) {
        val fc = field.getAnnotation(SqlFilterColumn.class);
        val mappedFieldName = fc == null || isEmpty(fc.mappedField()) ? field.getName() + "Mapped" : fc.mappedField();

        val mappedField = ReflectUtil.findField(parameterType, mappedFieldName);
        if (mappedField == null) return;

        setField(mappedField, param, Boolean.TRUE);
    }

    private ColumnInfo findColumn(Map<Integer, ColumnInfo> cols, Set<String> names) {
        for (val e : cols.entrySet()) {
            if (names.contains(e.getValue().getName())) return e.getValue();
        }

        return null;
    }

    private Set<String> createAllowedNames(Field field) {
        val filterColumn = field.getAnnotation(SqlFilterColumn.class);
        val fieldName = filterColumn != null ? filterColumn.value() : field.getName();

        return Sets.newHashSet(fieldName.toUpperCase(), NameUtil.toUpperUnderScore(fieldName));
    }
}
