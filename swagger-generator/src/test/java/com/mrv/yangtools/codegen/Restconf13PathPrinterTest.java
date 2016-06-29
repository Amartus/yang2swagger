package com.mrv.yangtools.codegen;

import com.mrv.yangtools.test.utils.MockNodeBuilder;
import org.junit.Test;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * @author bartosz.michalik@amartus.com
 */
public class Restconf13PathPrinterTest {


    @Test
    public void segment() throws Exception {

    }

    @Test
    public void simplePath() throws Exception {
        PathSegment a = new PathSegment(mock(SchemaContext.class)).withName("a");
        PathSegment b = new PathSegment(a).withName("b");
        PathSegment c = new PathSegment(b).withName("c");

        assertEquals("a/b/c/", new Restconf13PathPrinter(c, false).path());
    }


    @Test
    public void parametrizedPath() throws Exception {
        PathSegment a = new PathSegment(mock(SchemaContext.class)).withName("a");
        PathSegment b = new PathSegment(a).withName("b")
                .withListNode(new MockNodeBuilder("test")
                        .param("x")
                        .param("y")
                        .build()
                );
        PathSegment c = new PathSegment(b).withName("c");

        assertEquals("a/b={x},{y}/c/", new Restconf13PathPrinter(c, false).path());
    }

    @Test
    public void parametrizedPathLastSegment() throws Exception {
        PathSegment a = new PathSegment(mock(SchemaContext.class)).withName("a");
        PathSegment b = new PathSegment(a).withName("b")
                .withListNode(new MockNodeBuilder("test")
                        .param("x")
                        .param("y")
                        .build()
                );
        PathSegment c = new PathSegment(b).withName("c")
                .withListNode(new MockNodeBuilder("test").param("z").build());

        assertEquals("a/b={x},{y}/c={z}/", new Restconf13PathPrinter(c, false).path());
        assertEquals("a/b={x},{y}/c/", new Restconf13PathPrinter(c, false, true).path());
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

        assertEquals("mod1:a/mod1:b={x},{y}/mod2:c/", new Restconf13PathPrinter(c, true).path());
        assertEquals("a/b={x},{y}/c/", new Restconf13PathPrinter(c, false).path());
    }

}