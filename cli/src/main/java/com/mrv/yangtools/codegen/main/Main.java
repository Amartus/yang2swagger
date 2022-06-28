package com.mrv.yangtools.codegen.main;

import com.mrv.yangtools.codegen.impl.path.AbstractPathHandlerBuilder;
import com.mrv.yangtools.codegen.impl.path.odl.ODLPathHandlerBuilder;
import java.io.*;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mrv.yangtools.codegen.impl.path.rfc8040.PathHandlerBuilder;
import com.mrv.yangtools.codegen.impl.postprocessor.*;
import com.mrv.yangtools.common.ContextHelper;
import io.swagger.models.auth.BasicAuthDefinition;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mrv.yangtools.codegen.SwaggerGenerator;

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
	
	// RESTCONF uses yang-data+json or yang-data+xml as the content type.
	@Option(name= "-content-type", usage = "Content type the API generates / consumes - default application/yang-data+json")
	public String contentType = "application/yang-data+json";

    @Option(name = "-simplify-hierarchy", usage = "Use it to generate Swagger which with simplified inheritence model which can be used with standard code generators. Default false")
    public boolean simplified = false;

    @Option(name = "-use-namespaces", usage="Use namespaces in resource URI")
    public boolean useNamespaces = false;

    @Option(name = "-fullCrud", usage="If the flag is set to false path are generated for GET operations only. Default true")
    public boolean fullCrud = true;

    @Option(name="-elements", usage="Define YANG elements to focus on. Defaul DATA + RPC")
    public ElementType elementType = ElementType.DATA_AND_RPC;

    @Option(name = "-authentication", usage="Authentication definition")
    public AuthenticationMechanism authenticationMechanism = AuthenticationMechanism.NONE;

    @Option(name = "-use-odl-path-format", usage = "Select to use bierman-02 RESTCONF path format. Default false")
    public boolean odlPathFormat;

    @Option(name = "-basepath", usage="")
    public String basePath = "localhost:1234";

    public enum ElementType {
        DATA, RPC, DATA_AND_RPC;
    }

    public enum AuthenticationMechanism {
        BASIC, NONE
    }

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
            System.exit(-1);
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

        if(log.isInfoEnabled()) {
            String modulesSting = context.getModules().stream().map(ModuleIdentifier::getName).collect(Collectors.joining(", "));

            log.info("Modules found in the {} are {}", yangDir, modulesSting);
        }

        final Set<Module> toGenerate = context.getModules().stream().filter(m -> modules == null || modules.contains(m.getName()))
                .collect(Collectors.toSet());

        final AbstractPathHandlerBuilder pathHandler;
        if (odlPathFormat) {
            // bierman-02
            pathHandler = new ODLPathHandlerBuilder();
        } else {
            // rfc8040
            pathHandler = new PathHandlerBuilder();
        }

        if (!fullCrud) {
            pathHandler.withoutFullCrud();
        }
        if (useNamespaces) {
            pathHandler.useModuleName();
        }

        validate(basePath);

        final SwaggerGenerator generator = new SwaggerGenerator(context, toGenerate)
        		.version(apiVersion)
                .format(outputFormat).consumes(contentType).produces(contentType)
                .host(basePath)
                .pathHandler(pathHandler)
                .elements(map(elementType));

        generator
                .appendPostProcessor(new CollapseTypes());

        if(AuthenticationMechanism.BASIC.equals(authenticationMechanism)) {
            generator.appendPostProcessor(new AddSecurityDefinitions().withSecurityDefinition("api_sec", new BasicAuthDefinition()));
        }

        if(simplified) {
            generator.appendPostProcessor(new SingleParentInheritenceModel());
        }

        generator.appendPostProcessor(new Rfc4080PayloadWrapper());
        generator.appendPostProcessor(new RemoveUnusedDefinitions());

        generator.generate(new OutputStreamWriter(out));
    }

    private void validate(String basePath) {
        URI.create(basePath);
    }

    private SchemaContext buildSchemaContext(String dir, Predicate<Path> accept)
            throws ReactorException {
        if(dir.contains(File.pathSeparator)) {
            return ContextHelper.getFromDir(Arrays.stream(dir.split(File.pathSeparator)).map(s -> FileSystems.getDefault().getPath(s)), accept);
        } else {
            return ContextHelper.getFromDir(Stream.of(FileSystems.getDefault().getPath(dir)), accept);
        }
    }

    private SwaggerGenerator.Elements[] map(ElementType elementType) {
        switch(elementType) {
            case DATA:
                return new SwaggerGenerator.Elements[]{SwaggerGenerator.Elements.DATA};
            case RPC:
                return new SwaggerGenerator.Elements[]{SwaggerGenerator.Elements.RPC};
            case DATA_AND_RPC:
            default:
                return new SwaggerGenerator.Elements[]{SwaggerGenerator.Elements.DATA, SwaggerGenerator.Elements.RPC};
        }

    }
}
