package com.mrv.yangtools.codegen;

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.mrv.yangtools.common.BindingMapping.getClassName;
import static com.mrv.yangtools.common.BindingMapping.getPropertyName;

/**
 * @author bartosz.michalik@amartus.com
 */
public class DataObjectsBuilder {

    private static final Logger log = LoggerFactory.getLogger(DataObjectsBuilder.class);

    private Map<DataSchemaNode, String> names;
    private Set<String> built;

    private final TypeConverter converter;

    public DataObjectsBuilder(SchemaContext ctx) {
        names = new HashMap<>();
        built = new HashSet<>();
        converter = new AnnotatingTypeConverter(ctx);
    }

    public void processModule(Module module) {
        HashSet<String> cache = new HashSet<>(names.values());
        log.debug("processing data nodes defined in {}", module.getName());
        processNode(module, cache);

        log.debug("processing rcps defined in {}", module.getName());
        module.getRpcs().forEach(r -> {
            processNode(r.getInput(), cache);
            processNode(r.getOutput(), cache);
        });
        log.debug("processing augmentations defined in {}", module.getName());
        module.getAugmentations().forEach(r -> {
            processNode(r, cache);
        });
    }

    private void processNode(DataNodeContainer container, HashSet<String> cache) {
        DataNodeIterable iter = new DataNodeIterable(container);

        StreamSupport.stream(iter.spliterator(), false).filter(n -> n instanceof ContainerSchemaNode || n instanceof ListSchemaNode)
                .filter(n -> ! names.containsKey(n))
                .forEach(n -> {
                    String name = generateName(n, cache);
                    names.put(n, name);
                });
    }


    String getName(DataSchemaNode node) {
        return names.get(node);
    }

    String getDefinitionId(DataSchemaNode node) {
        return "#/definitions/"+ names.get(node);
    }

    public <T extends DataSchemaNode & DataNodeContainer> Model build(T node) {
        final ModelImpl model = new ModelImpl();
        model.description(desc(node));
        model.setProperties(structure(node));

        built.add(getName(node));

        return model;
    }

    private <T extends DataSchemaNode & DataNodeContainer> Property refOrStructure(T node) {
        final boolean useReference = built.contains(getName(node));
        Property prop;
        if(useReference) {
            final String definitionId = getDefinitionId(node);
            log.debug("reference to {}", definitionId);
            prop = new RefProperty(definitionId);
        } else {
            log.debug("submodel for {}", getName(node));
            prop = new ObjectProperty(structure(node));
        }
        return prop;
    }

    private <T extends DataSchemaNode & DataNodeContainer> Map<String, Property> structure(T node) {
        return node.getChildNodes().stream().map(c -> {

            final String propertyName = getPropertyName(c.getQName().getLocalName());

            Property prop = null;

            if (c instanceof LeafListSchemaNode) {
                LeafListSchemaNode ll = (LeafListSchemaNode) c;
                prop = new ArrayProperty(getPropertyByType(ll));
            } else if (c instanceof LeafSchemaNode) {
                LeafSchemaNode lN = (LeafSchemaNode) c;
                prop = getPropertyByType(lN);
            } else if (c instanceof ContainerSchemaNode) {
                prop = refOrStructure((ContainerSchemaNode)c);
            } else if (c instanceof ListSchemaNode) {
                prop = new ArrayProperty().items(refOrStructure((ListSchemaNode)c));
            }

            if (prop != null) {
                prop.setReadOnly(!c.isConfiguration());
                prop.setDescription(desc(c));
            }

            return new Pair(propertyName, prop);
        }).collect(Collectors.toMap(pair -> pair.name, pair -> pair.property));
    }

    private Property getPropertyByType(LeafListSchemaNode llN) {
        return converter.convert(llN.getType(), llN);
    }

    private Property getPropertyByType(LeafSchemaNode lN) {

        final Property property = converter.convert(lN.getType(), lN);
        property.setDefault(lN.getDefault());

        return property;
    }

    public String desc(DocumentedNode node) {
        return  node.getReference() == null ? node.getDescription() :
                node.getDescription() + " REF:" + node.getReference();
    }

    private String generateName(DataSchemaNode node, HashSet<String> cache) {
        String name = getClassName(node.getQName());
        if(cache.contains(name)) {

            final Iterable<QName> path = node.getPath().getParent().getPathTowardsRoot();

            for(QName p : path) {
                name = getClassName(p) + name;
                if(! cache.contains(name)) break;
            }

            //TODO if still we have a problem add module name !!!
        }
        return name;
    }

    private static class Pair {
        final String name;
        final Property property;

        private Pair(String name, Property property) {
            this.name = name;
            this.property = property;
        }
    }
}
