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

import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class DataNodeHelper {
    public static Iterable<SchemaNode> iterable(DataNodeContainer container) {
        return new DataNodeIterable(container);
    }
    public static Stream<SchemaNode> stream(DataNodeContainer container) {
        return StreamSupport.stream(iterable(container).spliterator(), false);
    }
}
