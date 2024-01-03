/*
 *   Copyright (c) 2016-2024 MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Christopher Murch <cmurch@mrv.com>
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen;

import io.swagger.models.Swagger;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

import java.util.Collection;

/**
 * @author bartosz.michalik@amartus.com
 */
public interface PathHandlerBuilder {
    PathHandler forModule(Module module);

    void configure(EffectiveModelContext ctx, Swagger target, DataObjectBuilder builder);

    void addTagGenerator(TagGenerator generator);

    Collection<TagGenerator> getTagGenerators();
}
