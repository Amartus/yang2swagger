package com.mrv.yangtools.codegen.main;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.common.SchemaBuilder;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Please name the modules that you want to generate.");
            System.exit(1);
        }

        final List<String> modules = Arrays.asList(args);

        try {
            generate(System.getProperty("user.dir"), modules);
        } catch (Throwable t) {
            log.error(t.toString());
        }
    }

    public static void generate(String yangDir, List<String> modules) throws IOException, ReactorException {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.yang");

        final SchemaContext context = buildSchemaContext(yangDir, p -> matcher.matches(p.getFileName()));
        final Set<Module> toGenerate = context.getModules().stream().filter(
                m -> modules.contains(m.getName())).collect(Collectors.toSet());

        final SwaggerGenerator generator = new SwaggerGenerator(context, toGenerate)
                .format(SwaggerGenerator.Format.YAML)
                .consumes("application/xml")
                .produces("application/xml")
                .host("localhost:1234")
                .elements(SwaggerGenerator.Elements.DATA, SwaggerGenerator.Elements.RCP);

        generator.generate(new OutputStreamWriter(System.out));
    }

    public static SchemaContext buildSchemaContext(String dir, Function<Path, Boolean> accept) throws ReactorException, IOException {
        SchemaBuilder builder = new SchemaBuilder().accepts(accept);
        builder.add(FileSystems.getDefault().getPath(dir));
        return builder.build();
    }
}
