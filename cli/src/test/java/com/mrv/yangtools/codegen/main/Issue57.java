package com.mrv.yangtools.codegen.main;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import org.junit.Assert;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineParser;

import java.io.ByteArrayOutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Issue57 {
    private static String path;

    static {
        try {
            path = Paths.get(Issue57.class.getResource("/bug_57/").toURI()).toAbsolutePath().toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testRegular() {

        List<String> args = Stream.of(
                "-yang-dir",
                path
        ).collect(Collectors.toList());

        Swagger swagger = runParser(args);
        assertContainsOnly(swagger, s -> s.endsWith("Input"),
                "objects.createobject.Input","objects.updateobject.Input");
    }

    @Test
    public void testOptimized() {

        List<String> args = Stream.of(
                "-reuse-groupings",
                "-yang-dir",
                path
        ).collect(Collectors.toList());

        Swagger swagger = runParser(args);

        assertContainsOnly(swagger, s -> s.endsWith("Input"), "objects.createobject.Input");
    }

    private void assertContainsOnly(Swagger swagger, Predicate<String> filterDefs, String... name) {
        Set<String> result = swagger.getDefinitions().keySet().stream()
                .filter(filterDefs)
                .collect(Collectors.toSet());
        Set<String> expected = Stream.of(name)
                .collect(Collectors.toSet());
        Assert.assertEquals(expected, result);
    }

    private Swagger runParser(List<String> args) {
        Main main = new Main();
        CmdLineParser parser = new CmdLineParser(main);
        try {
            parser.parseArgument(args);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            main.out = baos;
            main.init();
            main.generate();

            return  new SwaggerParser().parse(new String(baos.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
