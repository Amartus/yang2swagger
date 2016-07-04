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
public class PutOperationGenerator extends OperationGenerator {
    public PutOperationGenerator(PathSegment path, DataObjectRepo repo) {
        super(path, repo);
    }

    @Override
    public Operation execute(DataSchemaNode node) {
        final Operation put = defaultOperation();
        final RefModel definition = new RefModel(getDefinitionId(node));
        put.description("creates or updates " + getName(node));
        put.parameter(new BodyParameter()
                .name("body-param")
                .schema(definition)
                .description(getName(node) + " to be added or updated"));

        put.response(201, new Response().description("Object created"));
        put.response(204, new Response().description("Object modified"));
        return put;
    }
}
