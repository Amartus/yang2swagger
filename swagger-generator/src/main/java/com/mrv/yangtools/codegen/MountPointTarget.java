/*
 *   Copyright (c) 2026.  MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *    Contributors:
 *       Christopher Murch <cmurch@mrv.com>
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/**
 * Represents a target for a mount-point: a module and a name (grouping, container or list).
 */
public class MountPointTarget {
    private final String module;
    private final String name;

    public MountPointTarget(String module, String name) {
        this.module = module;
        this.name = Objects.requireNonNull(name, "name is required");
    }

    public String getModule() {
        return module;
    }

    public String getName() {
        return name;
    }

    @JsonCreator
    public static MountPointTarget fromJson(Object value) {
        if (value instanceof String) {
            return parse((String) value);
        } else if (value instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
            Object moduleObj = map.get("module");
            Object nameObj = map.get("name");
            if (nameObj == null) {
                throw new IllegalArgumentException("Mapping target name is required");
            }
            String module = moduleObj != null ? moduleObj.toString().trim() : null;
            String name = nameObj.toString().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Mapping target name cannot be empty");
            }
            return new MountPointTarget(module != null && module.isEmpty() ? null : module, name);
        }
        throw new IllegalArgumentException("Invalid mapping target value: " + value);
    }

    public static MountPointTarget parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Mapping value cannot be empty");
        }
        String[] parts = value.split(":", 2);
        if (parts.length == 2) {
            String module = parts[0].trim();
            String name = parts[1].trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Mapping target name cannot be empty");
            }
            return new MountPointTarget(module.isEmpty() ? null : module, name);
        } else {
            String name = parts[0].trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Mapping target name cannot be empty");
            }
            return new MountPointTarget(null, name);
        }
    }

    @Override
    @JsonValue
    public String toString() {
        return (module != null ? module + ":" : "") + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MountPointTarget that = (MountPointTarget) o;
        return Objects.equals(module, that.module) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(module, name);
    }
}
