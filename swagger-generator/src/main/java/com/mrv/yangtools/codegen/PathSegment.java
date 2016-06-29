package com.mrv.yangtools.codegen;

import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
class PathSegment implements Iterable<PathSegment> {
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
        this.converter = new TypeConverter(ctx);
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
        log.debug("{} / {}", parent.name, name);
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

    public boolean isReadOnly() {
        return readOnly;
    }

    public PathSegment drop() {
        log.debug("dropping {} segment", name);
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


    protected List<Parameter> params() {
        final List<Parameter> params = parent.params();
        params.addAll(localParameters());

        return params;
    }

    protected List<Parameter> listParams() {
        return parent.params();
    }

    protected Collection<? extends Parameter> localParameters() {
        if(localParams == null) {
            if(node != null) {
                log.debug("processing parameters from attached node");
                final Set<String> existingNames = parent.params().stream().map(Parameter::getName).collect(Collectors.toSet());

                localParams = node.getKeyDefinition().stream()
                        .map(k -> {

                            final String name = existingNames.contains(k.getLocalName()) ?
                                    moduleName + "-"  + k.getLocalName()
                                    : k.getLocalName();

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

    private static PathSegment NULL = new PathSegment() {

        @Override
        public PathSegment drop() {
            return null;
        }

        @Override
        protected List<Parameter> params() {
            return new ArrayList<>();
        }

        @Override
        protected List<Parameter> listParams() {
            return params();
        }
    };
}
