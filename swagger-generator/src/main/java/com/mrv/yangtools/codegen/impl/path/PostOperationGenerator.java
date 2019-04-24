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
public class PostOperationGenerator extends OperationGenerator {
    private final boolean dropLastSegmentParameters;

    public PostOperationGenerator(PathSegment path, DataObjectRepo repo, boolean dropLastSegmentParameters) {
        super(path, repo);
        this.dropLastSegmentParameters = dropLastSegmentParameters;
    }

    @Override
    public Operation execute(DataSchemaNode node) {
        final Operation post = dropLastSegmentParameters ? listOperation() : defaultOperation();
        final RefModel definition = new RefModel(getDefinitionId(node));
        post.summary("creates " + getName(node));
        String description = node.getDescription() == null ? "creates " + getName(node) :
                node.getDescription();
        post.description(description);
        post.parameter(new BodyParameter()
                .name(getName(node) + ".body-param")
                .schema(definition)
                .description(getName(node) + " to be added to list"));

        post.response(201, new Response().description("Object created"));
        post.response(409, new Response().description("Object already exists"));
        return post;
    }

    private Operation listOperation() {
        Operation listOper = defaultOperation();
        listOper.setParameters(path.listParams());
        return listOper;
    }
}
