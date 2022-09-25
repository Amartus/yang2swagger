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

package com.mrv.yangtools.codegen.impl;

import java.util.Optional;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 *
 * @author bartosz.michalik@amartus.com
 */
public class ModuleUtils {
    private final EffectiveModelContext ctx;

    public ModuleUtils(EffectiveModelContext ctx) {
        this.ctx = ctx;
    }
    public String toModuleName(QNameModule qname) {
        Optional<Module> modules = ctx.findModule(qname);
        if(modules.isEmpty()) throw new IllegalStateException("no support for " + qname + " modules with name " + qname);
        return modules.get().getName();
    }

    public String toModuleName(SchemaNode node) {
        return toModuleName(node.getPath().getLastComponent().getModule());
    }
}
