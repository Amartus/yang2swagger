package com.mrv.yangtools.common;

import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangInferencePipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SchemaBuilder {

    final private  static PathMatcher yang = FileSystems.getDefault().getPathMatcher("glob:*.yang");

    private static final Logger log = LoggerFactory.getLogger(SchemaBuilder.class);
    private Function<Path, Boolean> accept;
    private List<Path> yangs;


    public SchemaBuilder() {
        accept = defaultYangMatcher();
        yangs = new ArrayList<>();
    }

    public static Function<Path, Boolean> defaultYangMatcher() {
        return (path) -> yang.matches(path.getFileName());
    }


    public SchemaBuilder accepts(Function<Path, Boolean> accept) {
        Objects.requireNonNull(accept);
        this.accept = accept;
        return this;
    }

    public SchemaBuilder add(Path path) throws IOException {
        if(Files.isDirectory(path)) {
            Files.walk(path)
                    .filter(p -> Files.isRegularFile(p) && accept.apply(p))
                    .forEach(p -> {if(!yangs.contains(p)) yangs.add(p); });
        }

        return this;
    }


    public SchemaContext build() throws ReactorException {
        final CrossSourceStatementReactor.BuildAction reactor = YangInferencePipeline.RFC6020_REACTOR.newBuild();
        SchemaContext resolveSchemaContext;
        log.info("Inspecting all defined yangs {}", yangs);
        final List<InputStream> yangsStreams = new ArrayList<>();

        try {
            for (Path y : yangs) {
                try {
                    yangsStreams.add(new FileInputStream(y.toFile()));
                } catch (FileNotFoundException e) {
                    throw new IllegalStateException(y + " is not a file");
                }
            }
            resolveSchemaContext = reactor.buildEffective(new ArrayList<>(yangsStreams));
            return resolveSchemaContext;
        } finally {

            yangsStreams.forEach(s -> {
                try {
                    s.close();
                } catch (IOException e) {
                    //ignore
                }
            });

        }

    }

    public static void main(String[] args) {
        System.out.println(System.getProperty("java.class.path", "."));
    }
}
