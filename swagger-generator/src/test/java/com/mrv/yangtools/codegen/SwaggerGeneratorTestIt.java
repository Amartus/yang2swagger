/*
 * Copyright (c) 2016 MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Christopher Murch <cmurch@mrv.com>
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mrv.yangtools.common.ContextHelper;
import io.swagger.models.Model;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.models.properties.Property;

import io.swagger.models.properties.RefProperty;
import org.junit.After;
import org.junit.Ignore;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerGeneratorTestIt {

    private static final Logger log = LoggerFactory.getLogger(SwaggerGeneratorTestIt.class);


    private Swagger swagger;

    @After
    public void printSwagger() throws IOException {
        if(log.isDebugEnabled() && swagger != null) {
            StringWriter writer = new StringWriter();
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            mapper.writeValue(writer, swagger);
            log.debug(writer.toString());
        }
    }

    @org.junit.Test
    public void testGenerateSimpleModule() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("simplest.yang"));

        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig();
        swagger = generator.generate();

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "SimpleRoot", "Children1", "Children2"
        )), defNames);

        assertEquals(new HashSet<>(Arrays.asList(
                "SimpleRoot", "Children1", "Children2"
        )), defNames);

        checkLeafrefAreFollowed("Children2", "parent-id", "integer");

        assertThat(swagger.getPaths().keySet(), hasItem("/data/simple-root/children1={id}/children2={children2-id}/"));
    }

    private void checkLeafrefAreFollowed(String modelName, String propertyName, String type) {
        Model model = swagger.getDefinitions().get(modelName);
        Property parentId = model.getProperties().get(propertyName);
        assertEquals(type, parentId.getType());
        assertFalse(parentId.getVendorExtensions().isEmpty());
        assertTrue(parentId.getVendorExtensions().containsKey("x-path"));
    }

    @org.junit.Test
    public void testGenerateReadOnlyModule() throws Exception {

        //having
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("read-only.yang"));

        final Consumer<Path> onlyGetOperationExists = p -> {
            assertEquals(1, p.getOperations().size());
            assertNotNull(p.getGet());
        };

        //when
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig();

        swagger = generator.generate();

        //then
        // for read only operations only one get operation
        swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("c2")).map(Map.Entry::getValue)
                .forEach(onlyGetOperationExists);
    }

    @org.junit.Test
    public void testGenerateGroupingsModuleOptimizing() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("with-groupings.yang"));

        //when
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig();
        generator.strategy(SwaggerGenerator.Strategy.optimizing);
        swagger = generator.generate();

        //then
        assertEquals(3, swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("g2-c-c1")).count());
        assertEquals(7, swagger.getDefinitions().keySet().size());
        assertThat(swagger.getDefinitions().keySet(), hasItems("G1", "G2", "G3"));
        Model model = swagger.getDefinitions().get("GroupingRoot");
        RefProperty groupingChild2 = (RefProperty) model.getProperties().get("grouping-child2");
        assertEquals("G2", groupingChild2.getSimpleRef());
    }

    @org.junit.Test
    public void testGenerateAugmentedGroupingsModuleOptimizing() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().endsWith("groupings.yang"));

        //when
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig();
        generator.strategy(SwaggerGenerator.Strategy.optimizing);
        swagger = generator.generate();

        //then
        assertEquals(3, swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("g2-c-c1")).count());
        assertEquals(9, swagger.getDefinitions().keySet().size());
        assertThat(swagger.getDefinitions().keySet(), hasItems("G1", "G2", "G3"));
        Model model = swagger.getDefinitions().get("GroupingRoot");
        RefProperty groupingChild2 = (RefProperty) model.getProperties().get("grouping-child2");
        assertEquals("GroupingChild2", groupingChild2.getSimpleRef());
    }

    @org.junit.Test
    public void testGenerateGroupingsModuleUnpacking() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("with-groupings.yang"));

        //when
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig();
        generator.strategy(SwaggerGenerator.Strategy.unpacking);
        swagger = generator.generate();

        //then
        assertEquals(3, swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("g2-c-c1")).count());
        assertEquals(8, swagger.getDefinitions().keySet().size());
        assertThat(swagger.getDefinitions().keySet(), not(hasItems("G1", "G2", "G3")));

        Model model = swagger.getDefinitions().get("GroupingRoot");
        RefProperty groupingChild2 = (RefProperty) model.getProperties().get("grouping-child2");
        assertEquals("GroupingChild2", groupingChild2.getSimpleRef());

    }

    @org.junit.Test
    public void testGenerateRCPModule() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("rpc-basic.yang"));

        final Consumer<Path> singlePostOperation = p -> {
            assertEquals(1, p.getOperations().size());
            assertNotNull(p.getPost());
        };

        //when
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig();
        swagger = generator.generate();

        //then
        Map<String, Path> paths = swagger.getPaths();
        assertEquals(5, paths.keySet().size());
        List<String> operations = paths.keySet().stream().filter(p -> p.startsWith("/operations")).collect(Collectors.toList());
        assertEquals(3, operations.size());
        paths.entrySet().stream().filter(e -> e.getKey().startsWith("/operations")).map(Map.Entry::getValue).forEach(singlePostOperation);

        Map<String, Model> definitions = swagger.getDefinitions();
        assertEquals(7, definitions.size());
    }

    @Ignore
    @org.junit.Test
    public void testGenerateRCPModuleWithAugmentations() throws Exception {
        List<String> yangs = Arrays.asList("rpc-basic.yang", "rpc-augmentations.yang");
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> yangs.contains(p.getFileName().toString()));

        //when
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig();
        swagger = generator.generate();

        //then
        Map<String, Path> paths = swagger.getPaths();
        assertEquals(6, paths.keySet().size());

        Map<String, Model> models = swagger.getDefinitions();
        assertEquals(8, models.size());
        Property augmented = models.get("CRes").getProperties().get("a-container");
        assertNotNull(augmented);
    }

    @org.junit.Test
    public void testGenerateAugmentation() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().startsWith("simple"));

        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig();
        swagger = generator.generate();

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "SimpleRoot", "Children1", "Children2", "AddedA", "AddedAChildren1"
        )), defNames);

        checkLeafrefAreFollowed("Children2", "parent-id", "integer");
        checkLeafrefAreFollowed("AddedA", "simpleAugmentation:a1", "string");
        assertThat(swagger.getPaths().keySet(), hasItem("/data/simple-root/added-a/children1/"));
    }

    @org.junit.Test
    public void testGenerateChoice() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("choice.yang"));

        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig();
        swagger = generator.generate();

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "Data", "Protocol"
        )), defNames);

        assertEquals(new HashSet<>(Arrays.asList(
                "/data/protocol/", "/data/protocol/data/"
        )), swagger.getPaths().keySet());

    }

    @org.junit.Test
    public void testGenerateEnum() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("enum-module.yang"));

        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig();
        swagger = generator.generate();

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(3, defNames.size());
        assertEquals(new HashSet<>(Arrays.asList(
                "SimpleEnum", "InnerEnum", "RootNode"
        )), defNames);
    }

}