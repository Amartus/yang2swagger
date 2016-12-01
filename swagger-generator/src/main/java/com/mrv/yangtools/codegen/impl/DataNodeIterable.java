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

import org.opendaylight.yangtools.yang.model.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * Iterator that is used to traverse all nodes that will constitute Swagger models.
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class DataNodeIterable implements Iterable<SchemaNode> {
    private static final Logger log = LoggerFactory.getLogger(DataNodeIterable.class);

    private final List<SchemaNode> allChildren;

    public DataNodeIterable(final DataNodeContainer container) {
        allChildren = new LinkedList<>();
        traverse(container);
        log.debug("{} elements available", allChildren.size());
    }

    @Override
    public Iterator<SchemaNode> iterator() {
        return allChildren.iterator();
    }

    @Override
    public void forEach(Consumer<? super SchemaNode> action) {
            allChildren.forEach(action);
    }

    @Override
    public Spliterator<SchemaNode> spliterator() {
        return allChildren.spliterator();
    }


    private void traverse(final DataNodeContainer dataNode) {
        if (dataNode == null) {
            return;
        }

        final Iterable<DataSchemaNode> childNodes = dataNode.getChildNodes();

        if (childNodes != null) {
            for (DataSchemaNode childNode : childNodes) {
                if (childNode.isAugmenting()) {
                    log.debug("skipping augmenting node {}", childNode.getPath());
                    continue;
                }
                allChildren.add(childNode);
                if (childNode instanceof DataNodeContainer) {
                    final DataNodeContainer containerNode = (DataNodeContainer) childNode;
                    traverse(containerNode);
                } else if (childNode instanceof ChoiceSchemaNode) {
                    final ChoiceSchemaNode choiceNode = (ChoiceSchemaNode) childNode;
                    final Set<ChoiceCaseNode> cases = choiceNode.getCases();
                    if (cases != null) {
                        log.debug("processing choice: {}", childNode.getQName().getLocalName());
                        for (final ChoiceCaseNode caseNode : cases) {
                            log.debug("traversing case  {}:{}", childNode.getQName().getLocalName(), caseNode.getQName().getLocalName());
                            traverse(caseNode);
                        }
                    }
                }
            }
        }
        traverseGroupings(dataNode);
    }

    private void traverseGroupings(final DataNodeContainer dataNode) {
        final Set<GroupingDefinition> groupings = dataNode.getGroupings();
        if (groupings != null) {
            groupings.forEach(n -> { allChildren.add(n); traverse(n); });
        }
    }
}
