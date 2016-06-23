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
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException, ReactorException {
        final SchemaContext ctx = new SchemaBuilder().add(Paths.get(args[0])).build();
        log.info("Context parsed {}", ctx);

        final List<String> modules = Arrays.asList("mef-services", "mef-interfaces");

        final Set<Module> toGenerate = ctx.getModules().stream().filter(m -> modules.contains(m.getName())).collect(Collectors.toSet());

        final SwaggerGenerator generator = new SwaggerGenerator(ctx, toGenerate);

        generator.generate(new FileWriter("example.yaml"));

    }
}
