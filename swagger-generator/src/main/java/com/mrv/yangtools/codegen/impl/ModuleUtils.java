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

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

import java.net.URI;
import java.util.Set;

/**
 *
 * @author bartosz.michalik@amartus.com
 */
public class ModuleUtils {
    private final SchemaContext ctx;

    public ModuleUtils(SchemaContext ctx) {
        this.ctx = ctx;
    }
    public String toModuleName(QName qname) {
        Set<Module> modules = ctx.findModuleByNamespace(qname.getModule().getNamespace());
        if(modules.size() != 1) throw new IllegalStateException("no support for " + modules.size() + " modules with name " + qname);
        return modules.iterator().next().getName();
    }

    public String toModuleName(URI uri) {
        Set<Module> modules = ctx.findModuleByNamespace(uri);
        if(modules.size() != 1) throw new IllegalStateException("no support for " + modules.size() + " modules with uri " + uri);
        return modules.iterator().next().getName();
    }

    public String toModuleName(SchemaNode node) {
        return toModuleName(node.getQName());
    }
}
