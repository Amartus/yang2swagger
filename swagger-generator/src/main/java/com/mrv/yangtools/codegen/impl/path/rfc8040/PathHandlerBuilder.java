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

package com.mrv.yangtools.codegen.impl.path.rfc8040;

import com.mrv.yangtools.codegen.PathHandler;
import com.mrv.yangtools.codegen.impl.path.AbstractPathHandlerBuilder;
import org.opendaylight.yangtools.yang.model.api.Module;

/**
 * @author bartosz.michalik@amartus.com
 */
public class PathHandlerBuilder extends AbstractPathHandlerBuilder {

    @Override
    public PathHandler forModule(Module module) {
        return new com.mrv.yangtools.codegen.impl.path.rfc8040.PathHandler(ctx, module, target, objBuilder, tagGenerators,fullCrud).useModuleName(useModuleName);
    }

    @Override
    protected PathHandlerBuilder thiz() {
        return this;
    }
}
