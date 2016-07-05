package com.mrv.yangtools.codegen.impl;

import com.mrv.yangtools.codegen.DataObjectRepo;
import com.mrv.yangtools.codegen.PathSegment;
import io.swagger.models.Operation;
import io.swagger.models.RefModel;
import io.swagger.models.Response;
import io.swagger.models.parameters.BodyParameter;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

/**
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
        post.description("creates " + getName(node));
        post.parameter(new BodyParameter()
                .name("body-param")
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
