package com.mrv.yangtools.example;

import com.mrv.yangtools.codegen.SwaggerGenerator;
import java.io.*;

/**
 * @author bartosz.michalik@amartus.com
 */
public class YamlGenerator {

    public static void main(String[] args) throws Exception {
        final SwaggerGenerator generator = GeneratorHelper.getGenerator("mef-services", "mef-interfaces");
        generator.generate(new OutputStreamWriter(System.out));

    }
}
