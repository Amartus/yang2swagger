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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

public class MountPointMappingsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testDeserializationFromString() throws Exception {
        String json = "{\"label1\": [\"module1:name1\", \"module2:name2\"]}";
        MountPointMappings mappings = mapper.readValue(json, MountPointMappings.class);

        assertEquals(1, mappings.asMap().size());
        List<MountPointTarget> targets = mappings.getTargets("label1");
        assertEquals(2, targets.size());
        assertEquals("module1", targets.get(0).getModule());
        assertEquals("name1", targets.get(0).getName());
        assertEquals("module2", targets.get(1).getModule());
        assertEquals("name2", targets.get(1).getName());
    }

    @Test
    public void testDeserializationFromObject() throws Exception {
        String json = "{\"label1\": [{\"module\": \"module1\", \"name\": \"name1\"}]}";
        MountPointMappings mappings = mapper.readValue(json, MountPointMappings.class);

        assertEquals(1, mappings.asMap().size());
        List<MountPointTarget> targets = mappings.getTargets("label1");
        assertEquals(1, targets.size());
        assertEquals("module1", targets.get(0).getModule());
        assertEquals("name1", targets.get(0).getName());
    }

    @Test
    public void testDeserializationMixed() throws Exception {
        String json = "{\"label1\": [\"module1:name1\", {\"module\": \"module2\", \"name\": \"name2\"}]}";
        MountPointMappings mappings = mapper.readValue(json, MountPointMappings.class);

        assertEquals(1, mappings.asMap().size());
        List<MountPointTarget> targets = mappings.getTargets("label1");
        assertEquals(2, targets.size());
        assertEquals("name1", targets.get(0).getName());
        assertEquals("name2", targets.get(1).getName());
    }

    @Test
    public void testDeserializationSingleItem() throws Exception {
        String json = "{\"label1\": \"module1:name1\"}";
        MountPointMappings mappings = mapper.readValue(json, MountPointMappings.class);

        assertEquals(1, mappings.asMap().size());
        List<MountPointTarget> targets = mappings.getTargets("label1");
        assertEquals(1, targets.size());
        assertEquals("name1", targets.get(0).getName());
    }

    @Test(expected = Exception.class)
    public void testInvalidFormat() throws Exception {
        String json = "{\"label1\": 123}";
        mapper.readValue(json, MountPointMappings.class);
    }
}
