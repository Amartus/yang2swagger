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

package com.mrv.yangtools.codegen;

import com.mrv.yangtools.codegen.impl.AbstractDataObjectBuilder;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Query API for YANG modules identifiers in Swagger model
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public interface DataObjectRepo {
    /**
     * Get definition id for node. Prerequisite is to have node's module traversed {@link AbstractDataObjectBuilder#processModule(Module)}.
     * @param node node
     * @return id
     */
    <T extends SchemaNode & DataNodeContainer> String getDefinitionId(T node);
    /**
     * Get name for data node. Prerequisite is to have node's module traversed {@link AbstractDataObjectBuilder#processModule(Module)}.
     * @param node node
     * @return name
     */
    <T extends SchemaNode & DataNodeContainer> String getName(T node);
}
