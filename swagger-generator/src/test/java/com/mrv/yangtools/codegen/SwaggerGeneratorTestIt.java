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

import org.junit.After;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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

        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules());
        swagger = generator.generate();

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "SimpleRoot", "Children1", "Children2"
        )), defNames);

        assertEquals(new HashSet<>(Arrays.asList(
                "SimpleRoot", "Children1", "Children2"
        )), defNames);

        checkLeafrefAreFollowed("Children2", "parentId", "integer");

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
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules());
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
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules());
        generator.strategy(SwaggerGenerator.Strategy.optimizing);
        swagger = generator.generate();

        //then
        assertEquals(2, swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("g2-c-c1")).count());
        assertEquals(7, swagger.getDefinitions().keySet().size());
        assertThat(swagger.getDefinitions().keySet(), hasItems("G1", "G2", "G3"));

    }

    @org.junit.Test
    public void testGenerateGroupingsModuleUnpacking() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("with-groupings.yang"));

        //when
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules());
        generator.strategy(SwaggerGenerator.Strategy.unpacking);
        swagger = generator.generate();

        //then
        assertEquals(2, swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("g2-c-c1")).count());
        assertEquals(7, swagger.getDefinitions().keySet().size());
        assertThat(swagger.getDefinitions().keySet(), not(hasItems("G1", "G2", "G3")));

    }

    @org.junit.Test
    public void testGenerateRCPModule() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("rcp.yang"));

        final Consumer<Path> singlePostOperation = p -> {
            assertEquals(1, p.getOperations().size());
            assertNotNull(p.getPost());
        };

        //when
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules());
        swagger = generator.generate();

        //then
        Map<String, Path> paths = swagger.getPaths();
        assertEquals(3, paths.keySet().size());
        paths.keySet().forEach(n -> n.startsWith("/operational"));
        paths.values().forEach(singlePostOperation);
    }

    @org.junit.Test
    public void testGenerateAugmentation() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().startsWith("simple"));

        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules());
        swagger = generator.generate();

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "SimpleRoot", "Children1", "Children2", "AddedA", "AddedAChildren1"
        )), defNames);

        checkLeafrefAreFollowed("Children2", "parentId", "integer");
        checkLeafrefAreFollowed("AddedA", "a1", "string");



        assertThat(swagger.getPaths().keySet(), hasItem("/data/simple-root/added-a/children1/"));
    }

    @org.junit.Test
    public void testGenerateChoice() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("choice.yang"));

        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules());
        swagger = generator.generate();

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "Data", "Protocol"
        )), defNames);

        assertEquals(new HashSet<>(Arrays.asList(
                "/data/protocol/", "/data/protocol/data/"
        )), swagger.getPaths().keySet());

    }

}