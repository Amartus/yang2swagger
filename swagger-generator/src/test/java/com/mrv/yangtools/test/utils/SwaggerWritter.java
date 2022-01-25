package com.mrv.yangtools.test.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.models.Swagger;

import java.io.IOException;
import java.io.Writer;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerWritter {
    public static void writeSwagger(Writer writer, Swagger swagger) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        try {
            mapper.writeValue(writer, swagger);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
