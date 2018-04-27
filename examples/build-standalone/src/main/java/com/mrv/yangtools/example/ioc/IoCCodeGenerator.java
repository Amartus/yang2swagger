/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Damian Mrozowicz <damian.mrozowicz@amartus.com>
 */

package com.mrv.yangtools.example.ioc;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Guice;
import com.mrv.yangtools.codegen.IoCSwaggerGenerator;
import com.mrv.yangtools.codegen.impl.path.SegmentTagGenerator;
import com.mrv.yangutils.codegen.JerseyServerCodegen;

import io.swagger.codegen.ClientOptInput;
import io.swagger.codegen.ClientOpts;
import io.swagger.codegen.CodegenConstants;
import io.swagger.codegen.DefaultGenerator;
import io.swagger.models.Swagger;

/**
 * Example of code generator chain configured via API
 * @author damian.mrozowicz@amartus.com
 */
public class IoCCodeGenerator {
    public static void main(String[] args) throws Exception {
        Guice.createInjector(new GeneratorInjector());
        IoCSwaggerGenerator generator;
        if(args.length == 1) {
            // prepare a default generator for a given directory with YANG modules and accept all of them for path
            // generation
            generator = IoCGeneratorHelper.getGenerator(new File(args[0]),m -> true);
        } else {
            // prepare a default generator for all YANG modules and from classpath and accept only these which name
            // starts from 'tapi'
            generator = IoCGeneratorHelper.getGenerator(m -> m.getName().startsWith("Tapi"));
        }
        generator.tagGenerator(new SegmentTagGenerator());
        // -------- configure path generator ---------------
        // for data tree generate only GET operations
//        generator.pathHandler(new PathHandlerBuilder().withoutFullCrud());
        // for data tree generate full CRUD (depending on config flag in yang modules
        Swagger swagger = generator.generate();

        JerseyServerCodegen codegenConfig = new JerseyServerCodegen();

        // referencing handler for x-path annotation
//        codegenConfig.addAnnotation("propAnnotation", "x-path", v ->
//                "@com.mrv.provision.di.rest.jersey.metadata.Leafref(\"" + v + "\")"
//        );

        ClientOpts clientOpts = new ClientOpts();

        Path target = Files.createTempDirectory("generated");
        codegenConfig.additionalProperties().put(CodegenConstants.API_PACKAGE, "com.mrv.provision.di.rest.jersey.tapi.api");
        codegenConfig.additionalProperties().put(CodegenConstants.MODEL_PACKAGE, "com.mrv.provision.di.rest.jersey.tapi.model");
        codegenConfig.setOutputDir(target.toString());

        // write swagger.yaml to the target
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.writeValue(new FileWriter(new File(target.toFile(), "tapi.yaml")), swagger);

        ClientOptInput opts = new ClientOptInput().opts(clientOpts).swagger(swagger).config(codegenConfig);
        new DefaultGenerator()
                .opts(opts)
                .generate();
    }
}
