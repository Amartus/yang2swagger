/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Damian Mrozowicz <damian.mrozowicz@amartus.com>
 */

package com.mrv.yangtools.codegen.impl.path.odl;

import com.mrv.yangtools.codegen.PathSegment;
import com.mrv.yangtools.test.utils.MockNodeBuilder;
import org.junit.Test;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;


public class ODLRestconfPathPrinterTest {

    @Test
    public void simplePath() throws Exception {
        PathSegment a = new PathSegment(mock(SchemaContext.class)).withName("a").withModule("mod1");
        PathSegment b = new PathSegment(a).withName("b");
        PathSegment c = new PathSegment(b).withName("c");

        assertEquals("mod1:a/b/c", new ODLRestconfPathPrinter(c, true).path());
    }


    @Test
    public void parametrizedPath() throws Exception {
        PathSegment a = new PathSegment(mock(SchemaContext.class)).withName("a").withModule("mod1");
        PathSegment b = new PathSegment(a).withName("b")
                .withListNode(new MockNodeBuilder("test")
                        .param("x")
                        .param("y")
                        .build()
                );
        PathSegment c = new PathSegment(b).withName("c");

        assertEquals("mod1:a/b/{x}/{y}/c", new ODLRestconfPathPrinter(c, true).path());
    }

    @Test
    public void parametrizedPathLastSegment() throws Exception {
        PathSegment a = new PathSegment(mock(SchemaContext.class)).withName("a").withModule("mod1");;
        PathSegment b = new PathSegment(a).withName("b")
                .withListNode(new MockNodeBuilder("test")
                        .param("x")
                        .param("y")
                        .build()
                );
        PathSegment c = new PathSegment(b).withName("c")
                .withListNode(new MockNodeBuilder("test").param("z").build());

        assertEquals("mod1:a/b/{x}/{y}/c/{z}", new ODLRestconfPathPrinter(c, true, false).path());
        assertEquals("a/b/{x}/{y}/c/{z}", new ODLRestconfPathPrinter(c, false, false).path());
        assertEquals("mod1:a/b/{x}/{y}/c/", new ODLRestconfPathPrinter(c, true, true).path());
        assertEquals("a/b/{x}/{y}/c/", new ODLRestconfPathPrinter(c, false, true).path());
    }

    @Test
    public void namedModule() throws Exception {
        PathSegment a = new PathSegment(mock(SchemaContext.class))
                .withName("a")
                .withModule("mod1");
        PathSegment b = new PathSegment(a).withName("b")
                .withListNode(new MockNodeBuilder("test")
                        .param("x")
                        .param("y")
                        .build()
                );
        PathSegment c = new PathSegment(b).withName("c").withModule("mod2");

        assertEquals("mod1:a/b/{x}/{y}/mod2:c", new ODLRestconfPathPrinter(c, true).path());
    }

}