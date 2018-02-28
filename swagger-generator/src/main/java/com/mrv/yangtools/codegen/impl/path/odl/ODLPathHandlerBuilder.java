/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Damian Mrozowicz <damian.mrozowicz@amartus.com>
 */
package com.mrv.yangtools.codegen.impl.path.odl;

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
 * @author damian.mrozowicz@amartus.com
 */
public class ODLPathHandlerBuilder implements com.mrv.yangtools.codegen.PathHandlerBuilder {
    private SchemaContext ctx;
    private Swagger target;
    private DataObjectBuilder objBuilder;
    private Set<TagGenerator> tagGenerators = new HashSet<>();
    private boolean fullCrud = true;

    @Override
    public PathHandler forModule(Module module) {
        return new ODLPathHandler(ctx, module, target, objBuilder, tagGenerators,fullCrud);
    }

    public ODLPathHandlerBuilder withoutFullCrud() {
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
