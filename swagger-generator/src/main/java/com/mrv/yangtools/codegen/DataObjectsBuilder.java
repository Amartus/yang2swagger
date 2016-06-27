package com.mrv.yangtools.codegen;

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.mrv.yangtools.common.BindingMapping.getClassName;
import static com.mrv.yangtools.common.BindingMapping.getPropertyName;

/**
 * @author bartosz.michalik@amartus.com
 */
public class DataObjectsBuilder {

    public Map<DataSchemaNode, String> names;
    private final TypeConverter converter;

    public DataObjectsBuilder(SchemaContext ctx) {
        names = new HashMap<>();
        converter = new TypeConverter(ctx);
    }

    public void processModule(Module module) {
        HashSet<String> cache = new HashSet<>();


        DataNodeIterable iter = new DataNodeIterable(module);
        final Stream<DataSchemaNode> targetStream = StreamSupport.stream(iter.spliterator(), false);

        targetStream.filter(n -> n instanceof ContainerSchemaNode || n instanceof ListSchemaNode)
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


    public Model build(ContainerSchemaNode node) {
        final ModelImpl model = new ModelImpl();
        model.description(desc(node));
        structure(node, model);

        return model;
    }

    public Model build(ListSchemaNode node) {
        final ModelImpl model = new ModelImpl();
        model.description(desc(node));
        structure(node, model);

        return model;
    }

    private void structure(DataNodeContainer node, ModelImpl model) {
        node.getChildNodes().forEach(c -> {

            Property prop = null;

            if(c instanceof LeafListSchemaNode) {
                LeafListSchemaNode ll = (LeafListSchemaNode) c;

                prop = new ArrayProperty(getPropertyByType(ll));
            } else  if(c instanceof LeafSchemaNode) {
                LeafSchemaNode lN = (LeafSchemaNode) c;
                prop = getPropertyByType(lN);
            } else if(c instanceof ContainerSchemaNode) {
                final String definitionId = getDefinitionId(c);
                prop = new RefProperty(definitionId);
            } else if(c instanceof ListSchemaNode) {
                final String definitionId = getDefinitionId(c);
                prop = new ArrayProperty().items(new RefProperty(definitionId));
            }

            if(prop != null) {
                prop.setReadOnly(!c.isConfiguration());
                prop.setDescription(desc(c));

                model.property(getPropertyName(c.getQName().getLocalName()), prop);
            }
        });
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
            final Iterable<QName> path = node.getPath().getPathTowardsRoot();

            for(QName p : path) {
                name = name + getClassName(p);
                if(! cache.contains(name)) break;
            }

            //TODO if still we have a problem add module name !!!
        }
        return name;
    }
}
