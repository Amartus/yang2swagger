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

import com.mrv.yangtools.codegen.PathHandler;
import com.mrv.yangtools.codegen.PathSegment;
import com.mrv.yangtools.codegen.TagGenerator;
import io.swagger.models.Swagger;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public class AbstractPathHandler implements PathHandler {
    protected Swagger target;
    private Set<TagGenerator> tagGenerators = new HashSet<>();

    @Override
    public void addPath(ContainerSchemaNode node, PathSegment path) {

    }

    @Override
    public void addPath(ListSchemaNode node, PathSegment path) {

    }

    @Override
    public void addPath(ContainerSchemaNode input, ContainerSchemaNode output, PathSegment path) {

    }

    @Override
    public void setUp(Swagger target) {
        this.target = target;
    }


    protected List<String> tags(PathSegment pathCtx) {
        List<String> tags = new ArrayList<>(tagGenerators.stream().flatMap(g -> g.tags(pathCtx).stream())
                .collect(Collectors.toSet()));
        Collections.sort(tags);
        return tags;
    }

    /**
     * Add tag generator
     * @param generator to be added
     * @return this
     */
    public void addTagGenerator(TagGenerator generator) {
        tagGenerators.add(generator);
    }
}
