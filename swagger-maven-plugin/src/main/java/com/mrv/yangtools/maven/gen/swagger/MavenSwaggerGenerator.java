/*
 * Copyright (c) 2016 MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Christopher Murch <cmurch@mrv.com>
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.maven.gen.swagger;

import com.google.common.base.Preconditions;
import com.mrv.yangtools.codegen.PathHandlerBuilder;
import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.codegen.impl.path.AbstractPathHandlerBuilder;
import com.mrv.yangtools.codegen.impl.path.SegmentTagGenerator;
import com.mrv.yangtools.codegen.impl.path.odl.ODLPathHandlerBuilder;

import org.apache.maven.project.MavenProject;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang2sources.spi.BasicCodeGenerator;
import org.opendaylight.yangtools.yang2sources.spi.BuildContextAware;
import org.opendaylight.yangtools.yang2sources.spi.MavenProjectAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple ODL maven plugin integration.
 * Supported properties that might influence generated Swagger definition:
 * <ul>
 *     <li><code>generator-mime</code> - to specify comma-separated mime types (e.g. xml,json)</li>
 *     <li><code>generator-elements</code> - comma-separated list of {@link com.mrv.yangtools.codegen.SwaggerGenerator.Elements}
 * </ul>
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class MavenSwaggerGenerator implements BasicCodeGenerator, BuildContextAware, MavenProjectAware {

    private static final Logger log = LoggerFactory.getLogger(MavenSwaggerGenerator.class);
    private MavenProject mavenProject;

    public static final String DEFAULT_OUTPUT_FORMAT = "json";
    
    public static final String DEFAULT_OUTPUT_BASE_DIR_PATH = "target" + File.separator + "generated-sources"
            + File.separator + "swagger-maven-api-gen";
    private File projectBaseDir;
    private Map<String, String> additionalConfig;
    private File resourceBaseDir;

    @Override
    public Collection<File> generateSources(SchemaContext schemaContext, File outputDir, Set<Module> modules) throws IOException {
        final File outputBaseDir = outputDir == null ? getDefaultOutputBaseDir() : outputDir;

        if(! outputBaseDir.exists()) {
            if(! outputBaseDir.mkdirs()) {
                throw new IllegalStateException("cannot create " + outputBaseDir);
            }
        }
        String swaggerName = getAdditionalConfigOrDefault("base-module", "yang");

        File output = new File(outputBaseDir, swaggerName + "." + getFileExtension());

        String version = getAdditionalConfigOrDefault("api-version", mavenProject.getVersion());
        List<String> mimes = Arrays.asList(getAdditionalConfigOrDefault("generator-mime", "json,xml").split(","));
        List<SwaggerGenerator.Elements> elements = Arrays.stream(getAdditionalConfigOrDefault("generator-elements", "DATA,RPC").split(","))
                .filter(e -> {try{ SwaggerGenerator.Elements.valueOf(e); return true;} catch(IllegalArgumentException ex) {return false;}})
                .map(SwaggerGenerator.Elements::valueOf).collect(Collectors.toList());
        
        String pathHandler = getPathHandlerFormat();
        String useNamespaces = getAdditionalConfigOrDefault("use-namespaces", "false");

        AbstractPathHandlerBuilder pathHandlerBuilder;
        
        if(pathHandler.equals("odl")) {
        	pathHandlerBuilder = new ODLPathHandlerBuilder();
        } else {
        	pathHandlerBuilder = new com.mrv.yangtools.codegen.impl.path.rfc8040.PathHandlerBuilder();
        }
        
        if(useNamespaces.equals("true")) {
        	pathHandlerBuilder = pathHandlerBuilder.useModuleName();
        }

        try(FileWriter fileWriter = new FileWriter(output)) {
            SwaggerGenerator generator = new SwaggerGenerator(schemaContext, modules)
                    .format(format())
                    .tagGenerator(new SegmentTagGenerator())
                    .pathHandler(pathHandlerBuilder)
            		.version(version);
            mimes.forEach(m -> { generator.consumes("application/"+ m); generator.produces("application/"+ m);});
            generator.elements(elements.toArray(new SwaggerGenerator.Elements[elements.size()]));
            generator.generate(fileWriter);
        }

        return Collections.singleton(output);
    }

    @Override
    public void setAdditionalConfig(Map<String, String> additionalConfiguration) {
        this.additionalConfig = additionalConfiguration;
    }

    @Override
    public void setResourceBaseDir(File resourceBaseDir) {
        this.resourceBaseDir = resourceBaseDir;
    }

    @Override
    public void setBuildContext(org.sonatype.plexus.build.incremental.BuildContext buildContext) {
        log.debug("bc {}", buildContext);
    }

    @Override
    public void setMavenProject(org.apache.maven.project.MavenProject project) {
        this.mavenProject = project;
        this.projectBaseDir = project.getBasedir();

    }
    
    private String getPathHandlerFormat() {
        String stringFormat = getAdditionalConfigOrDefault("path-format", "rfc8040");
        if(stringFormat.equals("odl")) {
        	return "odl";
        } else {
           return "rfc8040";
        }
    }
    
    private String getFileExtension() {
        String stringFormat = additionalConfig.get("swagger-format");
        if(stringFormat != null) {
        	return stringFormat;
        } else {
           return "swagger";
        }
    }
    
    private SwaggerGenerator.Format format() {
        String stringFormat = getAdditionalConfigOrDefault("swagger-format", DEFAULT_OUTPUT_FORMAT);
        if(stringFormat.equalsIgnoreCase("json")) {
            return SwaggerGenerator.Format.JSON;
        } else if(stringFormat.equalsIgnoreCase("yaml")) {
            return SwaggerGenerator.Format.YAML;
        } else {
            throw new IllegalStateException("cannot output format " + stringFormat);
        }
    }
    
    private File getDefaultOutputBaseDir() {
        File outputBaseDir;
        outputBaseDir = new File(DEFAULT_OUTPUT_BASE_DIR_PATH);
        setOutputBaseDirAsSourceFolder(outputBaseDir, mavenProject);
        log.debug("Adding " + outputBaseDir.getPath() + " as compile source root");
        return outputBaseDir;
    }

    private static void setOutputBaseDirAsSourceFolder(final File outputBaseDir, final MavenProject mavenProject) {
        Preconditions.checkNotNull(mavenProject, "Maven project needs to be set in this phase");
        mavenProject.addCompileSourceRoot(outputBaseDir.getPath());
    }

    private String getAdditionalConfigOrDefault(String key, String defaulted) {
        String value = additionalConfig.get(key);
        if(value != null) {
            return value;
        } else {
            return defaulted;
        }
    }
}
