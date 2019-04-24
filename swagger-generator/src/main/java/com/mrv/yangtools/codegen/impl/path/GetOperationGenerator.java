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
import io.swagger.models.Response;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class GetOperationGenerator extends OperationGenerator {
    public GetOperationGenerator(PathSegment path, DataObjectRepo repo) {
        super(path, repo);
    }

    @Override
    public Operation execute(DataSchemaNode node) {
        final Operation get = defaultOperation();
        get.summary("returns " + getName(node));
        String description = node.getDescription() == null ? "returns " + getName(node) :
                node.getDescription();
        get.description(description);
        get.response(200, new Response()
                .schema(new RefProperty(getDefinitionId(node)))
                .description(getName(node)));
        return get;
    }
}
