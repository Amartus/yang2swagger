package com.mrv.yangtools.codegen.impl.postprocessor;

import io.swagger.models.Path;
import org.junit.Test;

import java.util.Map;

import static com.mrv.yangtools.codegen.impl.postprocessor.AbstractWithSwagger.Type.R;
import static org.junit.Assert.*;

/**
 * @author bartosz.michalik@amartus.com
 */
public class RemoveUnusedDefinitionsTest extends AbstractWithSwagger {

    @Test
    public void noChange() {
        int initial = swagger.getDefinitions().size();
        new RemoveUnusedDefinitions().accept(swagger);
        assertEquals(initial, swagger.getDefinitions().size());
    }


    @Test
    public void noChangeInClasses() {
        int initial = swagger.getDefinitions().size();

        Map<String, Path> paths = swagger.getPaths();
        paths.remove("/b/propE/propF");
        swagger.setPaths(paths);

        new RemoveUnusedDefinitions().accept(swagger);
        assertEquals(initial, swagger.getDefinitions().size());
    }

    @Test
    public void removingC() {
        int initial = swagger.getDefinitions().size();

        Map<String, Path> paths = swagger.getPaths().entrySet().stream()
                .filter(p -> !p.getKey().startsWith("/c")).collect(toMap());
        swagger.setPaths(paths);

        new RemoveUnusedDefinitions().accept(swagger);
        assertEquals(initial - 2, swagger.getDefinitions().size());
    }

    private void _removingB() {
        Map<String, Path> paths = swagger.getPaths().entrySet().stream()
                .filter(p -> !p.getKey().startsWith("/b")).collect(toMap());
        swagger.setPaths(paths);

        new RemoveUnusedDefinitions().accept(swagger);
    }

    @Test
    public void removingB() {
        int initial = swagger.getDefinitions().size();

        _removingB();

        assertEquals(initial - 3, swagger.getDefinitions().size());
    }

    @Test
    public void removingBWithModufication() {
        swagger.path("/c/propF", p("f", R));
        int initial = swagger.getDefinitions().size();

        _removingB();;

        assertEquals(initial - 2, swagger.getDefinitions().size());
    }
}