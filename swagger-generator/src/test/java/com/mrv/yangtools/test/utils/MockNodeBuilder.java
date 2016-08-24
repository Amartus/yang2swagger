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

package com.mrv.yangtools.test.utils;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.util.type.BaseTypes;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;

import static org.mockito.Mockito.*;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class MockNodeBuilder {

    private final QNameModule module;
    private final ArrayList<DataSchemaNode> params;
    private ArrayList<QName> names;

    public MockNodeBuilder(String module) {
        this.module = QNameModule.create(Paths.get("test", module).toUri(), new Date());
        this.params = new ArrayList<>();
        this.names = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public MockNodeBuilder param(String name) {

        LeafListSchemaNode leaf = mock(LeafListSchemaNode.class);
        QName qname = QName.create(module, name);

        when(leaf.getQName()).thenReturn(qname);
        when(leaf.getType()).thenReturn((TypeDefinition)BaseTypes.stringType());
        params.add(leaf);
        names.add(qname);
        return this;
    }


    public ListSchemaNode build() {
        ListSchemaNode result = mock(ListSchemaNode.class);

        when(result.getKeyDefinition()).thenReturn(names);
        when(result.getChildNodes()).thenReturn(params);

        return result;
    }

}
