package com.mrv.yangtools.test.utils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mrv.yangtools.codegen.SwaggerGenerator;
import io.swagger.models.Swagger;
import io.swagger.parser.Swagger20Parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerReader {

    Swagger read(Reader reader, SwaggerGenerator.Format format) throws IOException {
        ObjectMapper objectMapper = format == SwaggerGenerator.Format.JSON ?  new ObjectMapper(new JsonFactory())
                : new ObjectMapper(new YAMLFactory());

        return new Swagger20Parser().read(objectMapper.reader().readTree(reader));
    }

    public static void main(String[] args) throws IOException {
        InputStream input = SwaggerReader.class.getClassLoader().getResourceAsStream("json.yaml");
        Swagger swagger = new SwaggerReader().read(new InputStreamReader(input), SwaggerGenerator.Format.YAML);
        System.out.println(swagger);
    }
}
