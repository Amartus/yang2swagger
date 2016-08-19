package com.mrv.yangtools.example;

import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.common.SchemaBuilder;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author bartosz.michalik@amartus.com
 */
public class GeneratorHelper {
    private static final Logger log = LoggerFactory.getLogger(GeneratorHelper.class);
    public static SwaggerGenerator getGenerator(String... module) throws Exception {
        final List<String> modules = Arrays.asList(module);
        return getGenerator(m-> modules.contains(m.getName()));
    }

    public static SwaggerGenerator getGenerator(Predicate<Module> toSelect) throws Exception {
        return getGenerator(null, toSelect);
    }

    public static SwaggerGenerator getGenerator(File dir, Predicate<Module> toSelect) throws Exception {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.yang");

        Function<Path, Boolean> acc = p ->  matcher.matches(p.getFileName());

        final SchemaContext ctx = dir == null ? getFromClasspath(acc) : getFromDir(dir.toPath(), acc);
        log.info("Context parsed {}", ctx);

        final Set<Module> toGenerate = ctx.getModules().stream().filter(toSelect::test).collect(Collectors.toSet());

        final SwaggerGenerator generator = new SwaggerGenerator(ctx, toGenerate)
                .format(SwaggerGenerator.Format.YAML)
                .consumes("application/xml")
                .produces("application/xml")
                .host("localhost:1234")
                .elements(SwaggerGenerator.Elements.DATA, SwaggerGenerator.Elements.RCP);

        return generator;
    }
    public static SchemaContext getFromDir(Path dir, Function<Path, Boolean> accept) throws ReactorException {
        return getCtx(Stream.of(dir), accept);
    }

    public static SchemaContext getFromClasspath(Function<Path, Boolean> accept) throws ReactorException {
        return getCtx(Arrays.asList(System.getProperty("java.class.path", ".").split(File.pathSeparator))
                .stream().map(s -> Paths.get(s)), accept);
    }

    public static SchemaContext getCtx(Stream<Path> paths, Function<Path, Boolean> accept) throws ReactorException {

        SchemaBuilder builder = new SchemaBuilder().accepts(accept);

        paths.filter(p -> Files.isDirectory(p)).forEach((path) -> {
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
