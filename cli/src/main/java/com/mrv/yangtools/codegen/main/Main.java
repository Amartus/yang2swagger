package com.mrv.yangtools.codegen.main;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mrv.yangtools.common.ContextHelper;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.common.SchemaBuilder;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    @Option(name = "-yang-dir", usage = "Directory to search for YANG modules - defaults to current directory. " +
            "Multiple dirs might be separated by system path separator", metaVar = "path")
    public String yangDir = "";

    @Option(name = "-output", usage = "File to generate, containing the output - defaults to stdout", metaVar = "file")
    public String output = "";

    @Argument(multiValued = true, usage = "List of YANG module names to generate in swagger output", metaVar = "module ...")
    List<String> modules;
    
    @Option(name = "-format", usage = "Output format of generated file - defaults to yaml with options of json or yaml", metaVar = "enum")
    public SwaggerGenerator.Format outputFormat = SwaggerGenerator.Format.YAML;
    
    @Option(name = "-api-version", usage = "Version of api generated - default 1.0", metaVar = "file")
    public String apiVersion = "1.0";
    
    OutputStream out = System.out;

    public static void main(String[] args) {

        Main main = new Main();
        CmdLineParser parser = new CmdLineParser(main);

        try {
            parser.parseArgument(args);
            main.init();
            main.generate();
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
        } catch (Throwable t) {
            log.error("Error while generating Swagger", t);
        }
    }

    protected void init() throws FileNotFoundException {
        if (output != null && output.trim().length() > 0) {
            out = new FileOutputStream(output);
        }
    }

    protected void generate() throws IOException, ReactorException {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.yang");

        final SchemaContext context = buildSchemaContext(yangDir, p -> matcher.matches(p.getFileName()));
        final Set<Module> toGenerate = context.getModules().stream().filter(m -> modules == null || modules.contains(m.getName()))
                .collect(Collectors.toSet());

        final SwaggerGenerator generator = new SwaggerGenerator(context, toGenerate)
        		.version(apiVersion)
                .format(outputFormat).consumes("application/xml").produces("application/xml")
                .host("localhost:1234").elements(SwaggerGenerator.Elements.DATA, SwaggerGenerator.Elements.RCP);

        generator.generate(new OutputStreamWriter(out));
    }

    protected SchemaContext buildSchemaContext(String dir, Predicate<Path> accept)
            throws ReactorException, IOException {
        if(dir.contains(File.pathSeparator)) {
            return ContextHelper.getFromDir(Arrays.stream(dir.split(File.pathSeparator)).map(s -> FileSystems.getDefault().getPath(s)), accept);
        } else {
            return ContextHelper.getFromDir(Stream.of(FileSystems.getDefault().getPath(dir)), accept);
        }
    }
}
