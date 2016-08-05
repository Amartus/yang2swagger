package com.mrv.yangtools.example;

import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.codegen.impl.SegmentTagGenerator;
import com.mrv.yangutils.codegen.JerseyServerCodegen;
import io.swagger.codegen.*;
import io.swagger.models.Swagger;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author bartosz.michalik@amartus.com
 */
public class CodeGenerator {
    public static void main(String[] args) throws Exception {
        final SwaggerGenerator generator = GeneratorHelper.getGenerator("mef-services", "mef-interfaces");
        generator.tagGenerator(new SegmentTagGenerator());
        Swagger swagger = generator.generate();

        CodegenConfig codegenConfig = new JerseyServerCodegen();

        ClientOpts clientOpts = new ClientOpts();

        Path target = Files.createTempDirectory("generated");

        codegenConfig.additionalProperties().put(CodegenConstants.API_PACKAGE, "org.mef.restconf.api");
        codegenConfig.additionalProperties().put(CodegenConstants.MODEL_PACKAGE, "org.mef.restconf.model");
        codegenConfig.setOutputDir(target.toString());

        ClientOptInput opts = new ClientOptInput().opts(clientOpts).swagger(swagger).config(codegenConfig);

        new DefaultGenerator()
                .opts(opts)
                .generate();
    }
}
