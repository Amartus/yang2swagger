/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */
package com.mrv.yangtools.codegen.impl.postprocessor;

import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerRefHelper {
    private static final Logger log = LoggerFactory.getLogger(SwaggerRefHelper.class);

    /**
     * Get all references from model
     * @param type name
     * @param model definition
     * @return a stream with all other definitions that are part of composite
     */
    public static Stream<String> getReferences(String type, Model model) {
        if(model instanceof RefModel) return Stream.of(((RefModel)model).getSimpleRef());
        if(model instanceof ComposedModel) {
            return ((ComposedModel) model).getAllOf().stream().flatMap(of -> getReferences(type, of));
        }

        log.trace("Empty references for type {}", type);
        return Stream.empty();
    }

    /**
     * Get uses of of a set of properties in the model
     * @param type type name
     * @param model definition
     * @return a stream with all other definitions refrerenced via properties
     */
    public static Stream<String> getUses(String type, Model model) {
        if(model instanceof ModelImpl) {
            if(model.getProperties() == null) {
                if( ((ModelImpl) model).getEnum() == null)
                    log.warn("Empty properties while resolving {}", type);
                return Stream.empty();
            }
            return model.getProperties().values().stream().flatMap(SwaggerRefHelper::toUses);
        }
        if(model instanceof ComposedModel) {
            return ((ComposedModel) model).getAllOf().stream().flatMap(of -> getUses(type, of));
        }

        log.trace("Empty uses for type {}", type);
        return Stream.empty();
    }

    public static Stream<String> toUses(Property p) {
        if(p instanceof RefProperty) return Stream.of(((RefProperty)p).getSimpleRef());
        if(p instanceof ArrayProperty) return toUses(((ArrayProperty)p).getItems());

        if(p instanceof ObjectProperty) {
            return ((ObjectProperty)p).getProperties().values().stream()
                    .flatMap(SwaggerRefHelper::toUses);
        }

        return Stream.empty();
    }

    public static Stream<String> getFromResponses(Operation o ) {
        return o.getResponses().entrySet().stream().map(e -> getFromResponse(o, e.getKey())).filter(Objects::nonNull);
    }

    public static String getFromResponse(Operation oper, String responseCode) {
        Response response = oper.getResponses().get(responseCode);
        if(response == null) return null;

        Property prop = response.getSchema();

        if(prop instanceof ObjectProperty) {
            prop = ((ObjectProperty) prop).getProperties().get("output");
        }

        if(prop instanceof RefProperty) {
            return ((RefProperty)prop).getSimpleRef();
        }

        if(prop == null) return null;

        RefProperty schema = (RefProperty) prop;
        return schema.getSimpleRef();
    }

    public static String getFromBody(Operation oper) {
        Optional<Parameter> bodyParam =
                oper.getParameters().stream().filter(p -> p instanceof BodyParameter).findFirst();
        if(bodyParam.isPresent()) {
            Model schema = ((BodyParameter) bodyParam.get()).getSchema();
            if(schema instanceof RefModel) {
                return ((RefModel) schema).getSimpleRef();
            }
            if(schema instanceof ModelImpl) {
                Property input = schema.getProperties().get("input");
                if(input instanceof RefProperty)
                    return ((RefProperty) input).getSimpleRef();
            }
        }

        return null;
    }
}
