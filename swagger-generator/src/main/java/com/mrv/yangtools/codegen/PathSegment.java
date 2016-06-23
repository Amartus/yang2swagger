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
class PathSegment {
    private static final Logger log = LoggerFactory.getLogger(PathSegment.class);

    private final String name;
    private final String moduleName;
    private PathSegment parent;
    private Optional<ListSchemaNode> node;
    private TypeConverter converter;

    //local parameters
    private List<Parameter> localParams;



    /** only for {@link NullObject} !!! */
    protected PathSegment(String name) {
        this.name = name;
        this.moduleName = "";
    }

    PathSegment(String name, String moduleName, SchemaContext ctx) {
        this.name = name;
        this.moduleName = moduleName;
        parent = new NullObject();
        node = Optional.empty();
        converter = new TypeConverter(ctx);
    }

    public PathSegment attach(PathSegment child) {

        log.debug("Attaching new segment {} to {}", child.name, name);
        child.parent = this;
        return child;
    }

    public PathSegment drop() {
        log.debug("dropping {} segment", name);
        return this.parent;
    }

    @Override
    public String toString() {
        return path();
    }

    /**
     * Generate fully parametrized path
     * @return
     */
    public String path() {
        return parent.path() + segment(true);
    }

    /**
     * Generate path that do not include parameters for the last segment
     * @return
     */
    public String listPath() {
        return parent.path() + segment(false);
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
            if(node.isPresent()) {
                log.debug("processing parameters from attached node");
                final Set<String> existingNames = parent.params().stream().map(Parameter::getName).collect(Collectors.toSet());

                localParams = node.get().getKeyDefinition().stream()
                        .map(k -> {

                            final String name = existingNames.contains(k.getLocalName()) ?
                                    moduleName + "-"  + k.getLocalName()
                                    : k.getLocalName();

                            final PathParameter param = new PathParameter()
                                    .name(name);

                            final Optional<LeafSchemaNode> keyNode = node.get().getChildNodes().stream()
                                    .filter(n -> n instanceof LeafSchemaNode)
                                    .filter(n -> n.getQName().equals(k))
                                    .map(n -> ((LeafSchemaNode)n))
                                    .findFirst();

                            if(keyNode.isPresent()) {
                                final LeafSchemaNode kN = keyNode.get();
                                param
                                        .description("Id of " + node.get().getQName().getLocalName())
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

    protected String segment(boolean includeParams) {
        String pathString = name;
        if(includeParams && node.isPresent()) {
            pathString = pathString + "=" + localParameters().stream()
                                        .map(s -> "{" + s.getName() + "}")
                                        .collect(Collectors.joining(","));
        }
        pathString = pathString + "/";
        return pathString;
    }

    public void attachNode(ListSchemaNode node) {
        this.node = Optional.ofNullable(node);
    }

    private static class NullObject extends PathSegment{
        NullObject() {super("dummy");}

        @Override
        public PathSegment drop() {
            return null;
        }

        @Override
        protected List<Parameter> params() {
            return new ArrayList<>();
        }

        @Override
        public String path() {
            return "";
        }

        @Override
        public String listPath() {
            return path();
        }

        @Override
        protected List<Parameter> listParams() {
            return params();
        }
    }
}
