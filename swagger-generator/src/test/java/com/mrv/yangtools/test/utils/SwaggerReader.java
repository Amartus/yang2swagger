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
 * Example of Swagger data model reader
 * @author cmurch@mrv.com
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
