/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */
package com.mrv.yangtools.codegen.impl.path;

import com.mrv.yangtools.codegen.DataObjectBuilder;
import com.mrv.yangtools.codegen.PathHandlerBuilder;
import com.mrv.yangtools.codegen.TagGenerator;
import io.swagger.models.Swagger;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

import java.util.*;

/**
 * @author bartosz.michalik@amartus.com
 */
public abstract class AbstractPathHandlerBuilder implements PathHandlerBuilder {
    protected SchemaContext ctx;
    protected Swagger target;
    protected DataObjectBuilder objBuilder;
    protected Set<TagGenerator> tagGenerators = new HashSet<>();
    protected boolean useModuleName;
    protected boolean fullCrud = true;

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

    @Override
    public Collection<TagGenerator> getTagGenerators() {
        return tagGenerators;
    }


    public <T extends AbstractPathHandlerBuilder> T useModuleName() {
        useModuleName = true;
        return thiz();
    }

    protected abstract <T extends AbstractPathHandlerBuilder> T thiz();

    public <T extends AbstractPathHandlerBuilder> T withoutFullCrud() {
        fullCrud = false;
        return thiz();
    }
}
