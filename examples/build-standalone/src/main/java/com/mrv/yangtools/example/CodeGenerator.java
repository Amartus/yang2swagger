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
import com.mrv.yangtools.codegen.impl.SegmentTagGenerator;
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
            generator = GeneratorHelper.getGenerator(new File(args[0]),m -> m.getName().startsWith("Tapi"));
        } else {
            generator = GeneratorHelper.getGenerator(m -> m.getName().startsWith("Tapi"));
        }
//        final SwaggerGenerator generator = GeneratorHelper.getGenerator("Tapi");
//        final SwaggerGenerator generator = GeneratorHelper.getGenerator(new File("some directory"),m -> m.getName().startsWith("Tapi"));
        generator.tagGenerator(new SegmentTagGenerator());
        Swagger swagger = generator.generate();

        JerseyServerCodegen codegenConfig = new JerseyServerCodegen();
        codegenConfig.addAnnotation("propAnnotation", "x-path", v ->
                "@some.package.name.Leafref(\"" + v + "\")"
        );
        codegenConfig.addInterface("GlobalClass");

        ClientOpts clientOpts = new ClientOpts();

        Path target = Files.createTempDirectory("generated");
        codegenConfig.additionalProperties().put(CodegenConstants.API_PACKAGE, "com.mrv.provision.di.rest.jersey.tapi.api");
        codegenConfig.additionalProperties().put(CodegenConstants.MODEL_PACKAGE, "com.mrv.provision.di.rest.jersey.tapi.model");
        codegenConfig.setOutputDir(target.toString());

        // write swagerr.yaml to the target
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.writeValue(new FileWriter(new File(target.toFile(), "tapi.yaml")), swagger);

        ClientOptInput opts = new ClientOptInput().opts(clientOpts).swagger(swagger).config(codegenConfig);
        new DefaultGenerator()
                .opts(opts)
                .generate();
    }
}
