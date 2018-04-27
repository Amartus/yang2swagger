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

import com.mrv.yangtools.codegen.PathHandler;
import com.mrv.yangtools.codegen.impl.path.AbstractPathHandlerBuilder;
import org.opendaylight.yangtools.yang.model.api.Module;

/**
 * @author damian.mrozowicz@amartus.com
 */
public class ODLPathHandlerBuilder extends AbstractPathHandlerBuilder {

    @Override
    public PathHandler forModule(Module module) {
        return new ODLPathHandler(ctx, module, target, objBuilder, tagGenerators,fullCrud).useModuleName(useModuleName);
    }

    @Override
    protected ODLPathHandlerBuilder thiz() {
        return this;
    }
}
