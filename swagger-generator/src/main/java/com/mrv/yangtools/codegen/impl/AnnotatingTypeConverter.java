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

import io.swagger.models.properties.AbstractProperty;
import io.swagger.models.properties.Property;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.PathExpression;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;

import java.util.Optional;

/**
 * Annotate property with metadata for leafref
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class AnnotatingTypeConverter extends TypeConverter {
    public AnnotatingTypeConverter(EffectiveModelContext ctx) {
        super(ctx);
    }

    @Override
    public Property convert(TypeDefinition<?> type, SchemaNode parent) {
        Property prop = super.convert(type, parent);

        if(prop instanceof AbstractProperty && type instanceof LeafrefTypeDefinition) {
            toXpath((LeafrefTypeDefinition) type)
                    .ifPresent(xp -> ((AbstractProperty) prop).setVendorExtension("x-path", xp));
        }

        return prop;
    }

    @SuppressWarnings("UnstableApiUsage")
    private Optional<String> toXpath(LeafrefTypeDefinition node) {
        return Optional.ofNullable(node.getPathStatement()).map(PathExpression::getOriginalString);
    }
}
