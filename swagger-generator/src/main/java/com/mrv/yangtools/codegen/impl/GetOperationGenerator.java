package com.mrv.yangtools.codegen.impl;

import com.mrv.yangtools.codegen.DataObjectRepo;
import com.mrv.yangtools.codegen.PathSegment;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

/**
 * @author bartosz.michalik@amartus.com
 */
public class GetOperationGenerator extends OperationGenerator {
    public GetOperationGenerator(PathSegment path, DataObjectRepo repo) {
        super(path, repo);
    }

    @Override
    public Operation execute(DataSchemaNode node) {
        final Operation get = defaultOperation();
        get.description("returns " + getName(node));
        get.response(200, new Response()
                .schema(new RefProperty(getDefinitionId(node)))
                .description(getName(node)));
        return get;
    }
}
