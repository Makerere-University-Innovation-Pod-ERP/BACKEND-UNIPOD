package org.unipod.unipoderpbackend.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A hand-written copy of one of the frontend's zod object schemas. {@link #check} fails when a response
 * has a field the schema does not list, misses a required one, or carries a value of the wrong type.
 * Mirrors zod's rules: {@code z.string()} rejects null, {@code .nullable()} allows null,
 * {@code .optional()} allows the key to be absent (but not null).
 */
public final class ZodShape {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private enum Kind { STRING, NUMBER, BOOLEAN, ENUM, ARRAY, OBJECT }

    private record Field(Kind kind, boolean nullable, boolean optional, Set<String> values, ZodShape nested) {
    }

    private final Map<String, Field> fields = new LinkedHashMap<>();

    public static ZodShape object() {
        return new ZodShape();
    }

    public static JsonNode parse(String json) {
        return MAPPER.readTree(json);
    }

    public ZodShape string(String name) {
        return put(name, new Field(Kind.STRING, false, false, null, null));
    }

    public ZodShape nullableString(String name) {
        return put(name, new Field(Kind.STRING, true, false, null, null));
    }

    public ZodShape optionalString(String name) {
        return put(name, new Field(Kind.STRING, false, true, null, null));
    }

    public ZodShape number(String name) {
        return put(name, new Field(Kind.NUMBER, false, false, null, null));
    }

    public ZodShape nullableNumber(String name) {
        return put(name, new Field(Kind.NUMBER, true, false, null, null));
    }

    public ZodShape bool(String name) {
        return put(name, new Field(Kind.BOOLEAN, false, false, null, null));
    }

    public ZodShape enumOf(String name, String... values) {
        return put(name, new Field(Kind.ENUM, false, false, Set.of(values), null));
    }

    public ZodShape arrayOf(String name, ZodShape element) {
        return put(name, new Field(Kind.ARRAY, false, false, null, element));
    }

    public ZodShape objectOf(String name, ZodShape nested) {
        return put(name, new Field(Kind.OBJECT, false, false, null, nested));
    }

    /** Asserts {@code node} is an object that matches this schema exactly. */
    public void check(JsonNode node) {
        check(node, "$");
    }

    /** Asserts every element of an array matches this schema. */
    public void checkAll(JsonNode array) {
        assertTrue(array.isArray(), "expected an array");
        for (int index = 0; index < array.size(); index++) {
            check(array.get(index), "$[" + index + "]");
        }
    }

    private void check(JsonNode node, String path) {
        assertTrue(node.isObject(), path + " should be an object");
        Set<String> actual = new TreeSet<>(List.copyOf(node.propertyNames()));
        Set<String> allowed = new TreeSet<>(fields.keySet());
        Set<String> extra = new TreeSet<>(actual);
        extra.removeAll(allowed);
        assertEquals(Set.of(), extra, path + " has fields the frontend schema does not know");
        fields.forEach((name, field) -> {
            String where = path + "." + name;
            JsonNode value = node.get(name);
            if (value == null) {
                assertTrue(field.optional(), where + " is required but missing");
                return;
            }
            if (value.isNull()) {
                assertTrue(field.nullable(), where + " is null but the schema does not allow null");
                return;
            }
            switch (field.kind()) {
                case STRING -> assertTrue(value.isString(), where + " should be a string but is " + value);
                case NUMBER -> assertTrue(value.isNumber(), where + " should be a number but is " + value);
                case BOOLEAN -> assertTrue(value.isBoolean(), where + " should be a boolean but is " + value);
                case ENUM -> assertTrue(value.isString() && field.values().contains(value.asString()),
                        where + " should be one of " + field.values() + " but is " + value);
                case ARRAY -> {
                    assertTrue(value.isArray(), where + " should be an array");
                    for (int index = 0; index < value.size(); index++) {
                        field.nested().check(value.get(index), where + "[" + index + "]");
                    }
                }
                case OBJECT -> field.nested().check(value, where);
            }
        });
    }

    private ZodShape put(String name, Field field) {
        fields.put(name, field);
        return this;
    }
}
