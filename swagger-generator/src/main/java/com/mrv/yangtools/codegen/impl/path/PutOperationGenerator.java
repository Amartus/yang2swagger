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

package com.mrv.yangtools.codegen.impl.path;

import com.mrv.yangtools.codegen.DataObjectRepo;
import com.mrv.yangtools.codegen.PathSegment;
import io.swagger.models.Operation;
import io.swagger.models.RefModel;
import io.swagger.models.Response;
import io.swagger.models.parameters.BodyParameter;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class PutOperationGenerator extends OperationGenerator {
    public PutOperationGenerator(PathSegment path, DataObjectRepo repo) {
        super(path, repo);
    }

    @Override
    public Operation execute(DataSchemaNode node) {
        final Operation put = defaultOperation();
        final RefModel definition = new RefModel(getDefinitionId(node));
        put.summary("creates or updates " + getName(node));
        String description = node.getDescription() == null ? "creates or updates " + getName(node) :
                node.getDescription();
        put.description(description);
        put.parameter(new BodyParameter()
                .name(getName(node) + ".body-param")
                .schema(definition)
                .description(getName(node) + " to be added or updated"));

        put.response(201, new Response().description("Object created"));
        put.response(204, new Response().description("Object modified"));
        return put;
    }
}
