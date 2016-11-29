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

package com.mrv.yangtools.codegen.impl;

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.Swagger;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.model.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Used to convert YANG data nodes to Swagger models. The generator strategy is to unpack
 * all groupings attributes into container that use them.
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class UnpackingDataObjectsBuilder extends AbstractDataObjectBuilder {

    private static final Logger log = LoggerFactory.getLogger(UnpackingDataObjectsBuilder.class);

    private Set<String> built;

    /**
     * @param ctx YANG modules context
     * @param swagger for which models are built
     */
    public UnpackingDataObjectsBuilder(SchemaContext ctx, Swagger swagger, TypeConverter converter) {
        super(ctx, swagger, converter);
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(swagger);
        built = new HashSet<>();
    }

    /**
     * Build Swagger model for given Yang data node
     * @param node for which we want to build model
     * @param <T> YANG node type
     * @return Swagger model
     */
    public <T extends SchemaNode & DataNodeContainer> Model build(T node) {
        final ModelImpl model = new ModelImpl();
        model.description(desc(node));
        model.setProperties(structure(node));

        built.add(getName(node));

        return model;
    }
    /**
     * Get name for data node. Prerequisite is to have node's module traversed {@link UnpackingDataObjectsBuilder#processModule(Module)}.
     * @param node node
     * @return name
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer>  String getName(T node) {
        return names.get(node);
    }

    protected <T extends DataSchemaNode & DataNodeContainer> Property refOrStructure(T node) {
        final boolean useReference = built.contains(getName(node));
        Property prop;
        if(useReference) {
            final String definitionId = getDefinitionId(node);
            log.debug("reference to {}", definitionId);
            prop = new RefProperty(definitionId);
        } else {
            log.debug("submodel for {}", getName(node));
            prop = new ObjectProperty(structure(node, x -> true, x -> true));
        }
        return prop;
    }

}
