/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Damian Mrozowicz <damian.mrozowicz@amartus.com>
 */

package com.mrv.yangtools.codegen.odl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

import com.mrv.yangtools.codegen.DataObjectBuilder;
import com.mrv.yangtools.codegen.PathSegment;
import com.mrv.yangtools.codegen.TagGenerator;
import com.mrv.yangtools.codegen.impl.DeleteOperationGenerator;
import com.mrv.yangtools.codegen.impl.GetOperationGenerator;
import com.mrv.yangtools.codegen.impl.PostOperationGenerator;
import com.mrv.yangtools.codegen.impl.PutOperationGenerator;

import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.RefModel;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.RefProperty;

/**
 * REST path handler compliant with ODL RESTCONF https://wiki.opendaylight.org/view/OpenDaylight_Controller:MD-SAL:Restconf#Identifier_in_URI
 * @author damian.mrozowicz@amartus.com
 */
class ODLPathHandler implements com.mrv.yangtools.codegen.PathHandler {
    private final Swagger swagger;
    private final SchemaContext ctx;

    private final String data;
    private final String operations;
    private final String operational;
    private final Module module;
    private final DataObjectBuilder dataObjectBuilder;
    private final Set<TagGenerator> tagGenerators;
    private final  boolean fullCrud;

    ODLPathHandler(SchemaContext ctx, Module modules, Swagger target, DataObjectBuilder objBuilder, Set<TagGenerator> generators, boolean fullCrud) {
        this.swagger = target;
        this.ctx = ctx;
        this.module = modules;
        data = "/config/";
        operational = "/operational/";
        operations = "/operations/";
        this.dataObjectBuilder = objBuilder;
        this.tagGenerators = generators;
        this.fullCrud = fullCrud;
    }


    @Override
    public void path(ContainerSchemaNode cN, PathSegment pathCtx) {
    	final Path operationalPath = operationalOperations(cN, pathCtx);
    	ODLRestconfPathPrinter operationalPathPrinter = new ODLRestconfPathPrinter(pathCtx, false);
    	swagger.path(operational + operationalPathPrinter.path(), operationalPath);
    	
		if (!pathCtx.isReadOnly()) {
			final Path configPath = operations(cN, pathCtx);
	    	ODLRestconfPathPrinter configPathPrinter = new ODLRestconfPathPrinter(pathCtx, false);
	    	swagger.path(data + configPathPrinter.path(), configPath);
		}        
    }

    protected Path operationalOperations(DataSchemaNode node, PathSegment pathCtx) {
        final Path path = new Path();
        List<String> tags = tags(pathCtx);
        tags.add(module.getName());

        path.get(new GetOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));

        return path;
    }

    
    protected Path operations(DataSchemaNode node, PathSegment pathCtx) {
        final Path path = new Path();
        List<String> tags = tags(pathCtx);
        tags.add(module.getName());

        path.get(new GetOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
        if(fullCrud) {
            path.put(new PutOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
            path.post(new PostOperationGenerator(pathCtx, dataObjectBuilder, false).execute(node).tags(tags));
            path.delete(new DeleteOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
        }

        return path;
    }

    @Override
    public void path(ListSchemaNode lN, PathSegment pathCtx) {
    	final Path operationalPath = operationalOperations(lN, pathCtx);
    	ODLRestconfPathPrinter operationalPathPrinter = new ODLRestconfPathPrinter(pathCtx, false);
    	swagger.path(operational + operationalPathPrinter.path(), operationalPath);
    	
		if (!pathCtx.isReadOnly()) {
			final Path configPath = operations(lN, pathCtx);
	    	ODLRestconfPathPrinter configPathPrinter = new ODLRestconfPathPrinter(pathCtx, false);
	    	swagger.path(data + configPathPrinter.path(), configPath);
	    	
	    	if(fullCrud) {
	            //add list path
	            final Path list = new Path();
	            list.post(new PostOperationGenerator(pathCtx, dataObjectBuilder, true).execute(lN));


	            ODLRestconfPathPrinter postPrinter = new ODLRestconfPathPrinter(pathCtx, true);
	            swagger.path(data + postPrinter.path(), list);
	    	}
		}   
    }

    @Override
    public void path(ContainerSchemaNode input, ContainerSchemaNode output, PathSegment pathCtx) {
        final ODLRestconfPathPrinter printer = new ODLRestconfPathPrinter(pathCtx, false);

        Operation post = defaultOperation(pathCtx);

        post.tag(module.getName());
        if(input != null) {
            dataObjectBuilder.addModel(input);

            post.parameter(new BodyParameter()
                    .name("body-param")
                    .schema(new RefModel(dataObjectBuilder.getDefinitionId(input)))
                    .description(input.getDescription())
            );
        }

        if(output != null) {
            String description = output.getDescription();
            if(description == null) {
                description = "Correct response";
            }

            dataObjectBuilder.addModel(output);
            post.response(200, new Response()
                    .schema(new RefProperty(dataObjectBuilder.getDefinitionId(output)))
                    .description(description));
        }
        post.response(201, new Response().description("No response")); //no output body
        swagger.path(operations + printer.path(), new Path().post(post));
    }

    private List<String> tags(PathSegment pathCtx) {
        List<String> tags = new ArrayList<>(tagGenerators.stream().flatMap(g -> g.tags(pathCtx).stream())
                .collect(Collectors.toSet()));
        Collections.sort(tags);
        return tags;
    }

    private Operation defaultOperation(PathSegment pathCtx) {
        final Operation operation = new Operation();
        operation.response(400, new Response().description("Internal error"));
        operation.setParameters(pathCtx.params());
        return operation;
    }
}
