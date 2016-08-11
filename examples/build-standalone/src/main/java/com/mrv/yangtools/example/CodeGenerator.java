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
 * @author bartosz.michalik@amartus.com
 */
public class CodeGenerator {
    public static void main(String[] args) throws Exception {
//        final SwaggerGenerator generator = GeneratorHelper.getGenerator("mef-services", "mef-interfaces");
//        final SwaggerGenerator generator = GeneratorHelper.getGenerator(m -> m.getName().startsWith("mef-"));
        final SwaggerGenerator generator = GeneratorHelper.getGenerator(m -> m.getName().startsWith("Tapi"));
        generator.tagGenerator(new SegmentTagGenerator());
        Swagger swagger = generator.generate();

        CodegenConfig codegenConfig = new JerseyServerCodegen();

        ClientOpts clientOpts = new ClientOpts();

        Path target = Files.createTempDirectory("generated");

        codegenConfig.additionalProperties().put(CodegenConstants.API_PACKAGE, "com.mrv.provision.di.rest.jersey.tapi.api");
        codegenConfig.additionalProperties().put(CodegenConstants.MODEL_PACKAGE, "com.mrv.provision.di.rest.jersey.tapi.model");
        codegenConfig.setOutputDir(target.toString());

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.writeValue(new FileWriter(new File(target.toFile(), "swagger.yaml")), swagger);

        ClientOptInput opts = new ClientOptInput().opts(clientOpts).swagger(swagger).config(codegenConfig);
        new DefaultGenerator()
                .opts(opts)
                .generate();
    }
}
