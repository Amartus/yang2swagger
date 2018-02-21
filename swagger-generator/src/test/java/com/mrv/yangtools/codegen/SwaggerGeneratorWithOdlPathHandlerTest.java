/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Damian Mrozowicz <damian.mrozowicz@amartus.com>
 */

package com.mrv.yangtools.codegen;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Test;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mrv.yangtools.codegen.odl.ODLPathHandlerBuilder;
import com.mrv.yangtools.common.ContextHelper;

import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerGeneratorWithOdlPathHandlerTest {

    private static final Logger log = LoggerFactory.getLogger(SwaggerGeneratorWithOdlPathHandlerTest.class);


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

		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder());
        swagger = generator.generate();
        

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "simplest.SimpleRoot", "simplest.simpleroot.Children1", "simplest.simpleroot.children1.Children2"
        )), defNames);

        checkLeafrefAreFollowed("simplest.simpleroot.children1.Children2", "parent-id", "integer");

        assertThat(swagger.getPaths().keySet(), hasItem("/config/simplest:simple-root/simplest:children1/id/simplest:children2/children2-id/"));
        assertThat(swagger.getPaths().keySet(), hasItem("/operational/simplest:simple-root/simplest:children1/id/simplest:children2/children2-id/"));
    }
    
    @org.junit.Test
    public void testGenerateSimpleModuleWithLimitedDepth() throws Exception {
        //given
    	SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("simplest.yang"));
		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder()).maxDepth(2);       
        //when
        swagger = generator.generate();

        //then
        assertEquals(5, swagger.getPaths().keySet().size());
        assertEquals(3, swagger.getDefinitions().keySet().size());  
		assertThat(swagger.getPaths().keySet(),
				hasItems("/config/simplest:simple-root/",
						"/config/simplest:simple-root/simplest:children1/",
						"/config/simplest:simple-root/simplest:children1/id/",
						"/operational/simplest:simple-root/simplest:children1/id/",
						"/operational/simplest:simple-root/"));
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
        //when
		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder());
        swagger = generator.generate();

        //then
        // c2 element is read only, there should be only get with operational prefix for this element
        assertThat(swagger.getPaths(), notNullValue());
        

        final Consumer<Path> onlyGetOperationExists = p -> {
            assertEquals(1, p.getOperations().size());
            assertNotNull(p.getGet());
        };        
        System.out.println(swagger.getPaths().keySet());

		assertThat(swagger.getPaths().get("/config/read-only:simple-root/read-only:c1/id/read-only:c2/c2-id/"), nullValue());
		
		swagger.getPaths().entrySet().stream().filter(e -> e.getKey().startsWith("/operational/read-only:simple-root/read-only:c1/id/read-only:c2/c2-id")).map(Map.Entry::getValue)
				.forEach(onlyGetOperationExists);
        
        
        
    }

    @org.junit.Test
    public void testGenerateGroupingsModuleOptimizing() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("with-groupings.yang"));

        //when
		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder());
        generator.strategy(SwaggerGenerator.Strategy.optimizing);
        swagger = generator.generate();

        //then
        assertEquals(6, swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("g2-c-c1")).count());
        assertEquals(7, swagger.getDefinitions().keySet().size());
        assertThat(swagger.getDefinitions().keySet(), hasItems("with.groupings.groupingroot.G1", "with.groupings.G2", "with.groupings.g2.g2c.G3"));
        Model model = swagger.getDefinitions().get("with.groupings.GroupingRoot");
        RefProperty groupingChild2 = (RefProperty) model.getProperties().get("grouping-child2");
        assertEquals("with.groupings.G2", groupingChild2.getSimpleRef());
    }

    @org.junit.Test
    public void testGenerateAugmentedGroupingsModuleOptimizing() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().endsWith("groupings.yang"));

        //when
		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder());
        generator.strategy(SwaggerGenerator.Strategy.optimizing);
        swagger = generator.generate();

        //then
        assertEquals(6, swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("g2-c-c1")).count());
        assertEquals(3, swagger.getDefinitions().keySet().stream().filter(e -> e.contains("augmenting")).count());
        assertEquals(11, swagger.getDefinitions().keySet().size());
        assertThat(swagger.getDefinitions().keySet(), hasItems("with.groupings.groupingroot.G1", "with.groupings.G2", "with.groupings.g2.g2c.G3"));
        Model model = swagger.getDefinitions().get("with.groupings.GroupingRoot");
        RefProperty groupingChild2 = (RefProperty) model.getProperties().get("grouping-child2");
        assertEquals("with.groupings.groupingroot.GroupingChild2", groupingChild2.getSimpleRef());
    }
    
    @org.junit.Test
    public void testGenerateGroupingsModuleUnpacking() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("with-groupings.yang"));

        //when
		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder());
        generator.strategy(SwaggerGenerator.Strategy.unpacking);
        swagger = generator.generate();

        //then
        assertEquals(6, swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("g2-c-c1")).count());
        assertEquals(8, swagger.getDefinitions().keySet().size());
        assertThat(swagger.getDefinitions().keySet(), not(hasItems("G1", "G2", "G3")));

        Model model = swagger.getDefinitions().get("with.groupings.GroupingRoot");
        RefProperty groupingChild2 = (RefProperty) model.getProperties().get("grouping-child2");
        assertEquals("with.groupings.groupingroot.GroupingChild2", groupingChild2.getSimpleRef());

    }

    @org.junit.Test
    public void testGenerateRCPModule() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("rpc-basic.yang"));

        final Consumer<Path> singlePostOperation = p -> {
            assertEquals(1, p.getOperations().size());
            assertNotNull(p.getPost());
        };

        //when
		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder());
        swagger = generator.generate();

        //then
        Map<String, Path> paths = swagger.getPaths().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("/operations"))
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

        assertEquals(3, paths.keySet().size());
        paths.values().forEach(singlePostOperation);
    }

    @org.junit.Test
    public void testGenerateAugmentation() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().startsWith("simple"));

		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder());
        swagger = generator.generate();

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "simplest.simpleroot.Children1", "simplest.SimpleRoot", "simplest.simpleroot.children1.Children2",
                "simpleaugmentation.simpleroot.AddedA", "simpleaugmentation.simpleroot.addeda.Children1",
                "simpleaugmentation.Children1Augmentation1", "simpleaugmentation.SimpleRootAugmentation1"
        )), defNames);

        checkLeafrefAreFollowed("simplest.simpleroot.children1.Children2", "parent-id", "integer");
        checkLeafrefAreFollowed("simpleaugmentation.simpleroot.AddedA", "a1", "string");
        assertThat(swagger.getPaths().keySet(), hasItem("/operational/simplest:simple-root/simplest:added-a/simplest:children1/"));
    }

    @org.junit.Test
    public void testGenerateChoice() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("choice.yang"));

		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder());
        swagger = generator.generate();

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "choice.example.protocol.name.b.Data", "choice.example.Protocol"
        )), defNames);

        assertEquals(new HashSet<>(Arrays.asList(
                "/config/choice-example:protocol/", "/config/choice-example:protocol/choice-example:data/",
                "/operational/choice-example:protocol/", "/operational/choice-example:protocol/choice-example:data/"
        )), swagger.getPaths().keySet());

    }

    @org.junit.Test
    public void testGenerateEnum() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("enum-module.yang"));

		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder());
        swagger = generator.generate();

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(3, defNames.size());
        assertEquals(new HashSet<>(Arrays.asList(
                "enum.module.InnerEnum", "enum.module.RootNode", "enum.module.SimpleEnum"
        )), defNames);
    }


    @org.junit.Test
    public void testAugGroupEx() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getParent().getFileName().toString().equals("aug-group-ex"));

		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder());
        swagger = generator.generate();

        Model base = swagger.getDefinitions().get("base.Base");
        RefProperty c1 = (RefProperty) base.getProperties().get("c1");
        RefProperty c2 = (RefProperty) base.getProperties().get("c2");


        assertEquals("base.Coll",c1.getSimpleRef());
        assertEquals("base.base.C2",c2.getSimpleRef());
    }

    @org.junit.Test
    public void testInheritenceWithAugmentation() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getParent().getFileName().toString().equals("inheritence-with-augmentation"));

		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder());
        swagger = generator.generate();

        ComposedModel base = (ComposedModel) swagger.getDefinitions().get("base.Base");
        assertEquals(1, base.getInterfaces().size());
        assertEquals("base.Ident", base.getInterfaces().get(0).getSimpleRef());
        RefProperty managersRef = (RefProperty) ((ArrayProperty) base.getChild().getProperties().get("managers")).getItems();
        RefProperty usersRef = (RefProperty) ((ArrayProperty) base.getChild().getProperties().get("users")).getItems();


        ComposedModel managers = (ComposedModel) swagger.getDefinitions().get(managersRef.getSimpleRef());
        ComposedModel users = (ComposedModel) swagger.getDefinitions().get(usersRef.getSimpleRef());
        assertEquals(2, managers.getAllOf().size());
        assertEquals(2, managers.getInterfaces().size());
        //users are augmented
        assertEquals(3, users.getAllOf().size());
        assertEquals(2, users.getInterfaces().size());
    }

    @Test
    public void testDuplicatedNames() throws ReactorException {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("duplicated-names.yang"));
		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder());
        swagger = generator.generate();

        long nsCount = swagger.getDefinitions().keySet().stream().filter(n -> n.endsWith("Netnamespace")).count();

        assertEquals(2, nsCount);
    }
}