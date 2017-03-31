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

import io.swagger.models.Swagger;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

/**
 * @author bartosz.michalik@amartus.com
 */
public interface PathHandler {
    void addPath(ContainerSchemaNode node, PathSegment path);
    void addPath(ListSchemaNode node, PathSegment path);
    void addPath(ContainerSchemaNode input, ContainerSchemaNode output, PathSegment path);
    void setUp(Swagger target);
}
