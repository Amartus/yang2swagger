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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Concrete binding for mount-point mappings.
 */
public class MountPointMappings {
    private final Map<String, List<MountPointTarget>> mappings;

    @JsonCreator
    public static MountPointMappings fromMap(Map<String, Object> input) {
        if (input == null) return new MountPointMappings(Collections.emptyMap());
        Map<String, List<MountPointTarget>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof List) {
                List<MountPointTarget> targets = new java.util.ArrayList<>();
                for (Object item : (List<?>) value) {
                    targets.add(MountPointTarget.fromJson(item));
                }
                result.put(key, targets);
            } else if (value != null) {
                result.put(key, Collections.singletonList(MountPointTarget.fromJson(value)));
            }
        }
        return new MountPointMappings(result);
    }

    public MountPointMappings(Map<String, List<MountPointTarget>> mappings) {
        if (mappings == null) {
            this.mappings = Collections.emptyMap();
        } else {
            Map<String, List<MountPointTarget>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, List<MountPointTarget>> entry : mappings.entrySet()) {
                copy.put(entry.getKey(), Collections.unmodifiableList(new java.util.ArrayList<>(entry.getValue())));
            }
            this.mappings = Collections.unmodifiableMap(copy);
        }
    }

    @JsonValue
    public Map<String, List<MountPointTarget>> asMap() {
        return mappings;
    }

    public List<MountPointTarget> getTargets(String label) {
        return mappings.getOrDefault(label, Collections.emptyList());
    }

    public boolean isEmpty() {
        return mappings.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MountPointMappings that = (MountPointMappings) o;
        return Objects.equals(mappings, that.mappings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mappings);
    }

    @Override
    public String toString() {
        return mappings.toString();
    }
}
