package com.mrv.yangtools.example;

import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.common.SchemaBuilder;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangInferencePipeline;
import org.opendaylight.yangtools.yang.parser.util.NamedFileInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException, ReactorException {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.yang");

        final SchemaContext ctx = getFromClasspath(p ->  matcher.matches(p.getFileName()));
        log.info("Context parsed {}", ctx);

        final List<String> modules = Arrays.asList("mef-services", "mef-interfaces");

        final Set<Module> toGenerate = ctx.getModules().stream().filter(m -> modules.contains(m.getName())).collect(Collectors.toSet());

        final SwaggerGenerator generator = new SwaggerGenerator(ctx, toGenerate)
                .format(SwaggerGenerator.Format.YAML)
                .consumes("application/xml")
                .produces("application/xml")
                .host("localhost:1234")
                .elements(SwaggerGenerator.Elements.DATA, SwaggerGenerator.Elements.RCP);


        generator.generate(new OutputStreamWriter(System.out));

    }

    public static SchemaContext getFromClasspath(Function<Path, Boolean> accept) throws ReactorException {

        SchemaBuilder builder = new SchemaBuilder().accepts(accept);

        Arrays.asList(System.getProperty("java.class.path", ".").split(File.pathSeparator))
                .stream().map(s -> Paths.get(s)).filter(p -> Files.isDirectory(p)).forEach((path) -> {
            try {
                log.info("adding {}", path);
                builder.add(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return builder.build();
    }
}
