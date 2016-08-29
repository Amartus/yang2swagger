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
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public class GroupingHierarchyHandler {
    private static final Logger log = LoggerFactory.getLogger(GroupingHierarchyHandler.class);
    private final Map<QName, HierarchyNode> hierarchy;

    public GroupingHierarchyHandler(SchemaContext ctx) {
        hierarchy = buildHierarchy(ctx);
    }

    private Map<QName, HierarchyNode> buildHierarchy(SchemaContext ctx) {
        Map<QName, HierarchyNode> result = ctx.getGroupings().stream().map(g -> new Entry<>(g.getQName(), new HierarchyNode(g.getQName())))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

        ctx.getGroupings().forEach(g -> {
            HierarchyNode node = result.get(g.getQName());
            g.getUses().forEach(u -> {
                HierarchyNode parent = result.get(u.getGroupingPath().getLastComponent());
                if (parent == null) {
                    log.warn("Hierarchy creation problem. No grouping with name {} found. Ignoring hierarchy relation.", u.getGroupingPath().getLastComponent());
                } else {
                    node.addParent(parent);
                }

            });
        });
        return result;
    }

    public boolean isParent(QName parentName, QName forNode) {
        HierarchyNode node = hierarchy.get(forNode);
        if(node == null) {
            log.warn("Node not found for name {}", forNode);
            return false;
        }
        return node.isChildOf(parentName);
    }


    static class HierarchyNode {
        private final Set<HierarchyNode> parents;
        private final QName name;

        private HierarchyNode(QName name) {
            this.name = name;
            parents = new HashSet<>();
        }

        public QName getName() {
            return name;
        }

        public boolean isNamed(QName name) {
            Objects.requireNonNull(name);
            return name.equals(this.name);
        }

        public boolean isChildOf(QName name) {
            Objects.requireNonNull(name);
            if(parents.isEmpty()) return false;
            return parents.stream()
                    .map(p -> isNamed(name) || p.isChildOf(name)).findFirst()
                    .orElse(false);
        }

        public void addParent(HierarchyNode parent) {
            parents.add(parent);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HierarchyNode)) return false;
            HierarchyNode that = (HierarchyNode) o;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

}
