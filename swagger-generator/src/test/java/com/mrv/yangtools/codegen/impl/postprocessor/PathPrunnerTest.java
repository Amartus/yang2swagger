package com.mrv.yangtools.codegen.impl.postprocessor;

import org.hamcrest.core.Every;
import org.hamcrest.core.StringStartsWith;
import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;

/**
 * @author bartosz.michalik@amartus.com
 */
public class PathPrunnerTest extends AbstractWithSwagger {


    @Test
    public void noChange() {
        int orgPathsCnt = swagger.getPaths().size();
        int orgDefCnt = swagger.getDefinitions().size();
        new PathPrunner().accept(swagger);
        assertEquals(orgPathsCnt, swagger.getPaths().size());
        assertEquals(orgDefCnt, swagger.getDefinitions().size());

    }

    @Test
    public void prunePathB() {
        int orgPathsCnt = swagger.getPaths().size();
        int orgDefCnt = swagger.getDefinitions().size();
        new PathPrunner()
                .prunePath("/b")
                .accept(swagger);
        assertEquals(orgPathsCnt - 4, swagger.getPaths().size());
        assertEquals(orgDefCnt, swagger.getDefinitions().size());
        Assert.assertThat(swagger.getPaths().keySet(), Every.everyItem(not(StringStartsWith.startsWith("/b"))));
    }

    @Test
    public void prunePathBA() {
        int orgPathsCnt = swagger.getPaths().size();
        int orgDefCnt = swagger.getDefinitions().size();
        new PathPrunner()
                .prunePath("/b/propE")
                .prunePath("/a")
                .accept(swagger);
        assertEquals(orgPathsCnt - 3, swagger.getPaths().size());
        assertEquals(orgDefCnt, swagger.getDefinitions().size());
        Assert.assertThat(swagger.getPaths().keySet(), Every.everyItem(not(StringStartsWith.startsWith("/b/propE"))));
        Assert.assertThat(swagger.getPaths().keySet(), Every.everyItem(not(StringStartsWith.startsWith("/a"))));
        Assert.assertThat(swagger.getPaths().keySet(), hasItem(StringStartsWith.startsWith("/b")));
    }

    @Test
    public void pruneParent2() {

        int orgDefCnt = swagger.getDefinitions().size();
        new PathPrunner()
                .withType("Parent1")
                .accept(swagger);
        assertEquals(2, swagger.getPaths().size());
        Assert.assertThat(swagger.getPaths().keySet(), hasItems(
                is("/a"),
                is("/b")
        ));
        assertEquals(orgDefCnt, swagger.getDefinitions().size());
    }


}