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

import com.mrv.yangtools.codegen.impl.path.odl.ODLPathHandlerBuilder;
import com.mrv.yangtools.common.ContextHelper;
import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.Path;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.RefProperty;
import org.junit.Test;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.*;
/**
 * @author damian.mrozowicz@amartus.com
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerGeneratorWithOdlPathHandlerTest extends AbstractItTest {

    @org.junit.Test
    public void testGenerateSimpleModule() {
        //when
        swaggerFor("simplest.yang", generator -> {
            generator.pathHandler(new ODLPathHandlerBuilder().useModuleName());
        });
        

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "simplest.SimpleRoot", "simplest.simpleroot.Children1", "simplest.simpleroot.children1.Children2"
        )), defNames);

        checkLeafrefAreFollowed("simplest.simpleroot.children1.Children2", "parent-id", "integer");

        final String resUri = "/simplest:simple-root/children1/{id}/children2/{children2-id}";

        assertThat(swagger.getPaths().keySet(), hasItem("/config" + resUri));
        assertThat(swagger.getPaths().keySet(), hasItem("/operational" + resUri));
        assertEquals(8, swagger.getPaths().keySet().size());
        assertEquals(3, swagger.getPaths().keySet().stream().filter(p -> p.startsWith("/operational")).count());
    }

    @org.junit.Test
    public void testGenerateSimpleModuleWithLimitedDepth() {
        //when
        swaggerFor("simplest.yang", generator -> {
            generator.pathHandler(new ODLPathHandlerBuilder().useModuleName()).maxDepth(2);
        });

        //then
        assertEquals(5, swagger.getPaths().keySet().size());
        assertEquals(3, swagger.getDefinitions().keySet().size());  
		assertThat(swagger.getPaths().keySet(),
				hasItems("/config/simplest:simple-root",
						"/config/simplest:simple-root/children1/",
						"/config/simplest:simple-root/children1/{id}",
						"/operational/simplest:simple-root/children1/{id}",
						"/operational/simplest:simple-root"));
    }

    @org.junit.Test
    public void testGenerateReadOnlyModule() {
        //when
        swaggerFor("read-only.yang", generator -> {
            generator.pathHandler(new ODLPathHandlerBuilder().useModuleName());
        });

        //then
        // c2 element is read only, there should be only get with operational prefix for this element
        assertThat(swagger.getPaths(), notNullValue());
        

        final Consumer<Path> onlyGetOperationExists = p -> {
            assertEquals(1, p.getOperations().size());
            assertNotNull(p.getGet());
        };

		assertThat(swagger.getPaths().get("/config/read-only:simple-root/c1/id/c2/c2-id"), nullValue());

        final String resUri = "/read-only:simple-root";

        assertThat(swagger.getPaths().keySet(), hasItem("/config" + resUri));
        assertThat(swagger.getPaths().keySet(), hasItem("/operational" + resUri));

		swagger.getPaths().entrySet().stream().filter(e -> e.getKey().startsWith("/operational/read-only:simple-root/c1/id/c2/c2-id")).map(Map.Entry::getValue)
				.forEach(onlyGetOperationExists);

    }

    @org.junit.Test
    public void testGenerateRPCModule() {
        //when
        swaggerFor("rpc-basic.yang", generator -> generator.pathHandler(new ODLPathHandlerBuilder().useModuleName()));

        //then
        Map<String, Path> paths = swagger.getPaths().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("/operations"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertEquals(4, paths.keySet().size());
        paths.values().forEach(singlePostOperation.andThen(correctRPCOperationModels));
    }

    @org.junit.Test
    public void testGenerateAugmentation() {
        //when
        swaggerFor(p -> p.getFileName().toString().startsWith("simple"), generator -> generator.pathHandler(new ODLPathHandlerBuilder().useModuleName()));

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "simplest.simpleroot.Children1", "simplest.SimpleRoot", "simplest.simpleroot.children1.Children2",
                "simpleaugmentation.simpleroot.AddedA", "simpleaugmentation.simpleroot.addeda.Children1",
                "simpleaugmentation.Children1Augmentation1", "simpleaugmentation.SimpleRootAugmentation1"
        )), defNames);

        checkLeafrefAreFollowed("simplest.simpleroot.children1.Children2", "parent-id", "integer");
        checkLeafrefAreFollowed("simpleaugmentation.simpleroot.AddedA", "a1", "string");
        assertThat(swagger.getPaths().keySet(), hasItem("/operational/simplest:simple-root/simpleAugmentation:added-a/children1"));
        //as augmented container is config false
        assertThat(swagger.getPaths().keySet(), not(hasItem("/config/simplest:simple-root/simpleAugmentation:added-a/children1")));
    }

    @org.junit.Test
    public void testGenerateChoice() throws Exception {
        SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("choice.yang"));

		SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig()
				.pathHandler(new ODLPathHandlerBuilder().useModuleName());
        swagger = generator.generate();

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "choice.example.protocol.name.b.Data", "choice.example.Protocol"
        )), defNames);

        assertEquals(new HashSet<>(Arrays.asList(
                "/config/choice-example:protocol", "/config/choice-example:protocol/data",
                "/operational/choice-example:protocol", "/operational/choice-example:protocol/data"
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