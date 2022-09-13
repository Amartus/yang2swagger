package com.mrv.yangtools.codegen.main;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import org.junit.Assert;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineParser;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Issue57 {

    @Test
    public void test() throws Exception {

        String path = Paths.get(Issue57.class.getResource("/bug_57/").toURI()).toAbsolutePath().toString();

        List<String> args = Stream.of(
                "-yang-dir",
                path
        ).collect(Collectors.toList());

        Main main = new Main();
        CmdLineParser parser = new CmdLineParser(main);
        parser.parseArgument(args);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        main.out = baos;
        main.init();
        main.generate();

        Swagger swagger = new SwaggerParser().parse(new String(baos.toByteArray(), StandardCharsets.UTF_8));

        Set<String> result = swagger.getDefinitions().keySet().stream()
                .filter(s -> s.endsWith("Input"))
                .collect(Collectors.toSet());
        Set<String> expected = Stream.of("objects.createobject.Input", "objects.updateobject.Input")
                .collect(Collectors.toSet());
        Assert.assertEquals(expected, result);

    }
}
