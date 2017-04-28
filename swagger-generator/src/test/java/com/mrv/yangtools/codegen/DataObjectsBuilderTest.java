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

import com.mrv.yangtools.codegen.impl.*;
import com.mrv.yangtools.common.ContextHelper;
import io.swagger.models.Model;
import io.swagger.models.Swagger;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.effective.ListEffectiveStatementImpl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
@RunWith(MockitoJUnitRunner.class)
public class DataObjectsBuilderTest {

    @Mock
    private Swagger swagger;
    private static SchemaContext ctx;
    private static Module groupings;

    @BeforeClass
    public static void initCtx() throws ReactorException {
        ctx = ContextHelper.getFromClasspath(p -> p.getFileName().toString().equals("with-groupings.yang"));
        groupings = ctx.getModules().iterator().next();
    }

    @Test
    public void testAddModelGroupings() throws Exception {
        //having
        UnpackingDataObjectsBuilder builder = new UnpackingDataObjectsBuilder(ctx, swagger, new AnnotatingTypeConverter(ctx));
        SchemaNode c1 = DataNodeHelper.stream(groupings).filter(n -> n.getQName().getLocalName().equals("c1")).findFirst().orElseThrow(IllegalArgumentException::new);
        //when
        builder.processModule(groupings);
        builder.addModel((ListEffectiveStatementImpl) c1);
        //then
        verify(swagger).addDefinition(eq("with.groupings.groupingroot.C1"), any(Model.class));
    }

    @Test
    public void testNameGroupingsUnpacking() throws Exception {
        //having
        UnpackingDataObjectsBuilder builder = new UnpackingDataObjectsBuilder(ctx, swagger, new AnnotatingTypeConverter(ctx));
        builder.processModule(groupings);
        //when & then
        assertTrue(namesMeetNodes(builder,
                x -> x.getQName().getLocalName().equals("g2-l1"), new HashSet<>(
                        Collections.singletonList("with.groupings.g2.g2c.G2L1")
                )));
    }

    @Test
    public void testNameGroupingsOptimizing() throws Exception {
        //having
        DataObjectBuilder builder = new OptimizingDataObjectBuilder(ctx, swagger, new AnnotatingTypeConverter(ctx));
        builder.processModule(groupings);
        //when & then
        assertTrue(namesMeetNodes(builder,
                x -> x.getQName().getLocalName().equals("g2-l1"), new HashSet<>(
                        Collections.singletonList("with.groupings.g2.g2c.G3")
                )));

    }

    @SuppressWarnings("unchecked")
    protected <T extends SchemaNode & DataNodeContainer> boolean namesMeetNodes(DataObjectRepo builder, Function<T, Boolean> considerNode, Set<String> requiredNames) {
        return ! DataNodeHelper.stream(groupings).map(x -> (T)x).filter(considerNode::apply)
                .map(builder::getName).filter(m -> !requiredNames.contains(m)).findFirst().isPresent();
    }
}