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

import com.mrv.yangtools.common.ContextHelper;
import io.swagger.models.Model;
import io.swagger.models.Path;
import io.swagger.models.properties.RefProperty;
import org.junit.Test;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerGeneratorTestIt extends AbstractItTest {

    @org.junit.Test
    public void testGenerateSimpleModule() {
        swaggerFor("simplest.yang");

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "simplest.SimpleRoot", "simplest.simpleroot.Children1", "simplest.simpleroot.children1.Children2"
        )), defNames);

        checkLeafrefAreFollowed("simplest.simpleroot.children1.Children2", "parent-id", "integer");

        assertThat(swagger.getPaths().keySet(), hasItem("/data/simple-root/children1={id}/children2={children2-id}"));
    }
    
    @org.junit.Test
    public void testGenerateSimpleModuleWithLimitedDepth() throws Exception {
        //given
    	SchemaContext ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("simplest.yang"));
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig().maxDepth(2);
        
        //when
        swagger = generator.generate();

        //then
        assertEquals(3, swagger.getPaths().keySet().size());
        assertEquals(3, swagger.getDefinitions().keySet().size());  
        assertThat(swagger.getPaths().keySet(), hasItems("/data/simple-root", "/data/simple-root/children1/", "/data/simple-root/children1={id}"));
    }    



    @org.junit.Test
    public void testGenerateReadOnlyModule() {

        final Consumer<Path> onlyGetOperationExists = p -> {
            assertEquals(1, p.getOperations().size());
            assertNotNull(p.getGet());
        };

        //when
        swaggerFor("read-only.yang");

        //then
        // for read only operations only one get operation
        swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("c2")).map(Map.Entry::getValue)
                .forEach(onlyGetOperationExists);
    }

    @org.junit.Test
    public void testGenerateGroupingsModuleOptimizing() {
        //when
        swaggerFor(p -> p.getFileName().toString().equals("with-groupings.yang"));

        //then
        assertEquals(3, swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("g2-c-c1")).count());
        assertEquals(7, swagger.getDefinitions().keySet().size());
        assertThat(swagger.getDefinitions().keySet(), hasItems("with.groupings.groupingroot.G1", "with.groupings.G2", "with.groupings.g2.g2c.G3"));
        Model model = swagger.getDefinitions().get("with.groupings.GroupingRoot");
        RefProperty groupingChild2 = (RefProperty) model.getProperties().get("grouping-child2");
        assertEquals("with.groupings.G2", groupingChild2.getSimpleRef());
    }


    @org.junit.Test
    public void testGenerateGroupingsModuleUnpacking() {
        //when
        swaggerFor(p -> p.getFileName().toString().equals("with-groupings.yang"),
                g -> g.strategy(SwaggerGenerator.Strategy.unpacking));

        //then
        assertEquals(3, swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("g2-c-c1")).count());
        assertEquals(8, swagger.getDefinitions().keySet().size());
        assertThat(swagger.getDefinitions().keySet(), not(hasItems("G1", "G2", "G3")));

        Model model = swagger.getDefinitions().get("with.groupings.GroupingRoot");
        RefProperty groupingChild2 = (RefProperty) model.getProperties().get("grouping-child2");
        assertEquals("with.groupings.groupingroot.GroupingChild2", groupingChild2.getSimpleRef());

    }

    @org.junit.Test
    public void testGenerateRPCModule() {


        //when

        swaggerFor("rpc-basic.yang");


        //then
        Map<String, Path> paths = swagger.getPaths().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("/operations"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertEquals(4, paths.keySet().size());
        paths.values().forEach(singlePostOperation.andThen(correctRPCOperationModels));
    }

    @org.junit.Test
    public void testGenerateChoice() {
        swaggerFor("choice.yang");

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "choice.example.protocol.name.b.Data", "choice.example.Protocol"
        )), defNames);

        assertEquals(new HashSet<>(Arrays.asList(
                "/data/protocol", "/data/protocol/data"
        )), swagger.getPaths().keySet());

    }

    @org.junit.Test
    public void testGenerateEnum() {
        swaggerFor("enum-module.yang");

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(3, defNames.size());
        assertEquals(new HashSet<>(Arrays.asList(
                "enum.module.InnerEnum", "enum.module.RootNode", "enum.module.SimpleEnum"
        )), defNames);
    }

    @Test
    public void testDuplicatedNames() {
        swaggerFor("duplicated-names.yang");


        long nsCount = swagger.getDefinitions().keySet().stream().filter(n -> n.endsWith("Netnamespace")).count();

        assertEquals(2, nsCount);
    }
}