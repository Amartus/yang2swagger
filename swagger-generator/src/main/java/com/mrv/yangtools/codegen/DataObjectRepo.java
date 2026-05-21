/*
 *   Copyright (c) 2016-2024 MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *    Contributors:
 *       Christopher Murch <cmurch@mrv.com>
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
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
    <T extends SchemaNode & DataNodeContainer> String getDefinitionRef(T node);
    /**
     * Get definition id to be used as a request body for write operations (POST/PUT/PATCH).
     * <p>
     * When the <em>split config/state</em> mode is disabled, or the node's subtree contains no
     * {@code config false} (state) nodes, this returns the very same reference as
     * {@link #getDefinitionRef(SchemaNode)}. Otherwise it returns a reference to a separate
     * {@code <name>Config} definition that omits the state-only nodes, so that write request
     * bodies never carry data the server would reject.
     * @param node node
     * @return id
     */
    <T extends SchemaNode & DataNodeContainer> String getWriteDefinitionRef(T node);
    /**
     * Get name for data node. Prerequisite is to have node's module traversed {@link AbstractDataObjectBuilder#processModule(Module)}.
     * @param node node
     * @return name
     */
    <T extends SchemaNode & DataNodeContainer> String getName(T node);
}
