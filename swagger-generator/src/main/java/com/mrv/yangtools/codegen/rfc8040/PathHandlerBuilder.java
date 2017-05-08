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

package com.mrv.yangtools.codegen.rfc8040;

import com.mrv.yangtools.codegen.DataObjectBuilder;
import com.mrv.yangtools.codegen.PathHandler;
import com.mrv.yangtools.codegen.TagGenerator;
import io.swagger.models.Swagger;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author bartosz.michalik@amartus.com
 */
public class PathHandlerBuilder implements com.mrv.yangtools.codegen.PathHandlerBuilder {
    private SchemaContext ctx;
    private Swagger target;
    private DataObjectBuilder objBuilder;
    private Set<TagGenerator> tagGenerators = new HashSet<>();
    private boolean fullCrud = true;

    @Override
    public PathHandler forModule(Module module) {
        return new com.mrv.yangtools.codegen.rfc8040.PathHandler(ctx, module, target, objBuilder, tagGenerators,fullCrud);
    }

    public PathHandlerBuilder withoutFullCrud() {
        fullCrud = false;
        return this;
    }

    @Override
    public void configure(SchemaContext ctx, Swagger target, DataObjectBuilder builder) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(target);
        Objects.requireNonNull(builder);
        objBuilder = builder;
        this.ctx = ctx;
        this.target = target;
    }

    @Override
    public void addTagGenerator(TagGenerator generator) {
        Objects.requireNonNull(generator);
        this.tagGenerators.add(generator);
    }
}
