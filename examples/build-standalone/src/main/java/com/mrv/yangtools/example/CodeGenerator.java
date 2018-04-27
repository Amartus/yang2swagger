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

package com.mrv.yangtools.example;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.codegen.impl.path.SegmentTagGenerator;
import com.mrv.yangtools.codegen.impl.path.rfc8040.PathHandlerBuilder;
import com.mrv.yangutils.codegen.JerseyServerCodegen;
import io.swagger.codegen.*;
import io.swagger.models.Swagger;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Example of code generator chain configured via API
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class CodeGenerator {
    public static void main(String[] args) throws Exception {
        SwaggerGenerator generator;
        if(args.length == 1) {
            // prepare a default generator for a given directory with YANG modules and accept all of them for path
            // generation
            generator = GeneratorHelper.getGenerator(new File(args[0]),m -> true);
        } else {
            // prepare a default generator for all YANG modules and from classpath and accept only these which name
            // starts from 'tapi'
            generator = GeneratorHelper.getGenerator(m -> m.getName().startsWith("tapi"));
        }
        generator.tagGenerator(new SegmentTagGenerator());
        // -------- configure path generator ---------------
        // for data tree generate only GET operations
//        generator.pathHandler(new PathHandlerBuilder().withoutFullCrud());
        // for data tree generate full CRUD (depending on config flag in yang modules
        generator.pathHandler(new PathHandlerBuilder());
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
