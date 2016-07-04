package com.mrv.yangtools.codegen.impl;

import com.mrv.yangtools.codegen.DataObjectRepo;
import com.mrv.yangtools.codegen.PathSegment;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

/**
 * @author bartosz.michalik@amartus.com
 */
public class DeleteOperationGenerator extends OperationGenerator {
    public DeleteOperationGenerator(PathSegment path, DataObjectRepo repo) {
        super(path, repo);
    }

    @Override
    public Operation execute(DataSchemaNode node) {
        final Operation delete = defaultOperation();
        delete.description("removes " + getName(node));
        delete.response(204, new Response().description("Object deleted"));
        return delete;
    }
}
