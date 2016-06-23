package com.mrv.yangtools.example;

import com.amartus.yangtools.codegen.SwaggerGenerator;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
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

    static final String META_INF_YANG_STRING = "META-INF" + File.separator + "yang";
    private static Path dir = Paths.get("/media/sf_transfer/yang");
    public static void main(String[] args) throws IOException {
        final SchemaContext ctx = parse(dir);
        log.info("Context parsed {}", ctx);

        final List<String> modules = Arrays.asList("mef-services", "mef-interfaces");

        final Set<Module> toGenerate = ctx.getModules().stream().filter(m -> modules.contains(m.getName())).collect(Collectors.toSet());

        final SwaggerGenerator generator = new SwaggerGenerator(ctx, toGenerate);

        generator.generate(new FileWriter("example.yaml"));

    }

    private static SchemaContext parse(Path dir) {
        final CrossSourceStatementReactor.BuildAction reactor = YangInferencePipeline.RFC6020_REACTOR.newBuild();
        SchemaContext resolveSchemaContext;
        List<Closeable> closeables = new ArrayList<>();
        log.info("Inspecting {}", dir);
        try {
            /*
             * Collect all files which affect YANG context. This includes all
             * files in current project and optionally any jars/files in the
             * dependencies.
             */
            final Collection<File> yangFilesInProject = getYangFiles(dir);

            final List<NamedFileInputStream> yangsInProject = new ArrayList<>();
            for (final File f : yangFilesInProject) {
                // FIXME: This is hack - normal path should be reported.
                yangsInProject.add(new NamedFileInputStream(f, META_INF_YANG_STRING + File.separator + f.getName()));
            }

            List<InputStream> all = new ArrayList<>();
            all.addAll(yangsInProject);
            closeables.addAll(yangsInProject);

            /**
             * Set contains all modules generated from input sources. Number of
             * modules may differ from number of sources due to submodules
             * (parsed submodule's data are added to its parent module). Set
             * cannot contains null values.
             */
            Set<Module> projectYangModules;
            try {
                resolveSchemaContext = reactor.buildEffective(all);

                Set<Module> parsedAllYangModules = resolveSchemaContext.getModules();
                projectYangModules = new HashSet<>();
                for (Module module : parsedAllYangModules) {
                    final String path = module.getModuleSourcePath();
                    if (path != null) {
                        log.debug("Looking for source {}", path);
                        for (NamedFileInputStream is : yangsInProject) {
                            log.debug("In project destination {}", is.getFileDestination());
                            if (path.equals(is.getFileDestination())) {
                                log.debug("Module {} belongs to current project", module);
                                projectYangModules.add(module);
                                break;
                            }
                        }
                    }
                }
            } finally {
                for (AutoCloseable closeable : closeables) {
                    closeable.close();
                }
            }

            log.info("yang files parsed from {}", yangsInProject);
            return resolveSchemaContext;

            // MojoExecutionException is thrown since execution cannot continue
        } catch (Exception e) {
            log.error("Unable to parse yang files from {}", dir, e);

            throw new IllegalArgumentException(" Unable to parse ynag files from " +
                    dir, e);
        }
    }

    private static Collection<File> getYangFiles(Path dir) throws IOException {
        final PathMatcher yang = FileSystems.getDefault().getPathMatcher("glob:*.yang");
        return Files.walk(dir)
                .filter(p -> Files.isRegularFile(p) && yang.matches(p.getFileName()))
                .map(Path::toFile).collect(Collectors.toList());
    }
}
