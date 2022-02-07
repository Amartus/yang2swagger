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

import com.mrv.yangtools.codegen.impl.TypeConverter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Helper class that help to keep track of current location in YANG module data tree.
 * Segment stores current node related data and can point to its parent that
 * effectively allows for path computation from current node to root node.
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class PathSegment implements Iterable<PathSegment> {
    private static final Logger log = LoggerFactory.getLogger(PathSegment.class);

    private String name;
    private String moduleName;
    private PathSegment parent;
    private ListSchemaNode node;
    private TypeConverter converter;

    //local parameters
    private List<Parameter> localParams;
    private boolean readOnly;

    /**
     * To create a root segment of path
     * @param ctx YANG context
     */
    public PathSegment(SchemaContext ctx) {
        this(NULL);
        this.converter = new TypeConverter(ctx) {
            @Override
            protected boolean enumToModel() {
                return false;
            }
        };
    }

    private PathSegment() {}

    public PathSegment(PathSegment parent) {
        Objects.requireNonNull(parent);
        this.parent = parent;
        this.converter = parent.converter;
        this.moduleName = parent.moduleName;
        this.readOnly = parent.readOnly;
        node = null;
    }

    public PathSegment withName(String name) {
        log.debug("adding {} to {}", name, parent.name);
        this.name = name;
        return this;
    }

    public PathSegment withModule(String module) {
        this.moduleName = module;
        return this;
    }

    public PathSegment withListNode(ListSchemaNode node) {
        this.node = node;
        return this;
    }

    public PathSegment asReadOnly(boolean readOnly) {
        if(!parent.readOnly) {
            this.readOnly = readOnly;
        } else {
            // https://tools.ietf.org/html/rfc6020#section-7.19.1
            log.debug("parent {} is read-only ignoring current flag", parent.name);
        }
        return this;
    }

    public String getName() { return name;}
    public String getModuleName() { return moduleName;}
    public Collection<? extends Parameter> getParam() { return localParameters();}

    public boolean forList() {
        return isCollection() && !node.getKeyDefinition().isEmpty();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean isCollection() {
        return node != null;
    }

    public PathSegment drop() {
        log.debug("dropping {} segment", name);
        return parent();
    }

    public PathSegment parent() {
        return this.parent;
    }

    @Override
    public String toString() {
        return "PathSegment{" +
                "name='" + name + '\'' +
                ", moduleName='" + moduleName + '\'' +
                ", parent=" + parent +
                ", node=" + node +
                ", converter=" + converter +
                ", localParams=" + localParams +
                ", readOnly=" + readOnly +
                '}';
    }


    public List<Parameter> params() {
        final List<Parameter> params = parent.params();
        params.addAll(localParameters());

        return params;
    }

    public List<Parameter> listParams() {
        return parent.params();
    }

    protected Collection<? extends Parameter> localParameters() {
        if(localParams == null) {
            if(isCollection()) {
                log.debug("processing parameters from attached node");
                final Set<String> existingNames = parent.params().stream().map(Parameter::getName).collect(Collectors.toSet());

                localParams = node.getKeyDefinition().stream()
                        .map(k -> {

                            final String name = generateName(k, existingNames);

                            final PathParameter param = new PathParameter()
                                    .name(name);

                            final Optional<LeafSchemaNode> keyNode = node.getChildNodes().stream()
                                    .filter(n -> n instanceof LeafSchemaNode)
                                    .filter(n -> n.getQName().equals(k))
                                    .map(n -> ((LeafSchemaNode)n))
                                    .findFirst();

                            if(keyNode.isPresent()) {
                                final LeafSchemaNode kN = keyNode.get();
                                param
                                    .description("Id of " + node.getQName().getLocalName())
                                    .property(converter.convert(kN.getType(), kN));
                            }

                            return param;
                        })
                        .collect(Collectors.toList());
            } else {
                localParams = Collections.emptyList();
            }
        }

        return localParams;
    }

    protected String generateName(QName paramName, Set<String> existingNames) {
        String name = paramName.getLocalName();
        if(! existingNames.contains(name)) return name;
        name = this.name + "-" + name;

        if(! existingNames.contains(name)) return name;

        name = moduleName + "-" + name;

        if(! existingNames.contains(name)) return name;

        //brute-force
        final String tmpName = name;

        return IntStream.range(1, 102)
                .mapToObj(i -> tmpName + i)
                .filter(n -> !existingNames.contains(n))
                .findFirst().orElseThrow(IllegalStateException::new);
    }

    @Override
    public Iterator<PathSegment> iterator() {
        return new Iterator<PathSegment>() {

            private PathSegment current = PathSegment.this;

            @Override
            public boolean hasNext() {
                return current != NULL;
            }

            @Override
            public PathSegment next() {
                PathSegment r = current;
                current = current.parent;
                return r;
            }
        };
    }

    public Stream<PathSegment> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    private static PathSegment NULL = new PathSegment() {

        @Override
        public PathSegment drop() {
            return null;
        }

        @Override
        public List<Parameter> params() {
            return new ArrayList<>();
        }

        @Override
        public List<Parameter> listParams() {
            return params();
        }
    };
}
