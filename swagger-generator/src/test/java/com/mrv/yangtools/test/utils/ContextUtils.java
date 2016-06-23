package com.mrv.yangtools.test.utils;

import com.mrv.yangtools.common.SchemaBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Function;

/**
 * @author bartosz.michalik@amartus.com
 */
public class ContextUtils {
    private static final Logger log = LoggerFactory.getLogger(ContextUtils.class);
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

    public static void main(String[] args) throws ReactorException {
        SchemaContext ctx = ContextUtils.getFromClasspath(SchemaBuilder.defaultYangMatcher());

    }
}
