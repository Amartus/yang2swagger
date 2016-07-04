package com.mrv.yangtools.codegen;

import com.mrv.yangtools.codegen.impl.DataNodeIterable;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.*;
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
 * Used to convert YANG data nodes to Swagger models
 * @author bartosz.michalik@amartus.com
 */
public class DataObjectsBuilder implements DataObjectRepo {

    private static final Logger log = LoggerFactory.getLogger(DataObjectsBuilder.class);

    private Map<DataSchemaNode, String> names;
    private Set<String> built;

    private final TypeConverter converter;

    /**
     * @param ctx YANG modules context
     */
    public DataObjectsBuilder(SchemaContext ctx) {
        names = new HashMap<>();
        built = new HashSet<>();
        converter = new AnnotatingTypeConverter(ctx);
    }

    /**
     * Traverse model to collect all verbs from YANG nodes that will constitute Swagger models
     * @param module to traverse
     */
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

    /**
     * Build Swagger model for given Yang data node
     * @param node for which we want to build model
     * @param <T> YANG node type
     * @return Swagger model
     */
    public <T extends DataSchemaNode & DataNodeContainer> Model build(T node) {
        final ModelImpl model = new ModelImpl();
        model.description(desc(node));
        model.setProperties(structure(node));

        built.add(getName(node));

        return model;
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


    /**
     * Get name for data node. Prerequisite is to have node's module traversed {@link DataObjectsBuilder#processModule(Module)}.
     * @param node node
     * @return name
     */
    public String getName(DataSchemaNode node) {
        return names.get(node);
    }

    /**
     * Get definition id for node. Prerequisite is to have node's module traversed {@link DataObjectsBuilder#processModule(Module)}.
     * @param node node
     * @return id
     */
    public String getDefinitionId(DataSchemaNode node) {
        return "#/definitions/"+ names.get(node);
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

    private Pair prop(DataSchemaNode node) {
        final String propertyName = getPropertyName(node.getQName().getLocalName());

        Property prop = null;

        if (node instanceof LeafListSchemaNode) {
            LeafListSchemaNode ll = (LeafListSchemaNode) node;
            prop = new ArrayProperty(getPropertyByType(ll));
        } else if (node instanceof LeafSchemaNode) {
            LeafSchemaNode lN = (LeafSchemaNode) node;
            prop = getPropertyByType(lN);
        } else if (node instanceof ContainerSchemaNode) {
            prop = refOrStructure((ContainerSchemaNode) node);
        } else if (node instanceof ListSchemaNode) {
            prop = new ArrayProperty().items(refOrStructure((ListSchemaNode) node));
        }

        if (prop != null) {
            prop.setReadOnly(!node.isConfiguration());
            prop.setDescription(desc(node));
        }

        return new Pair(propertyName, prop);
    }

    private <T extends DataSchemaNode & DataNodeContainer> Map<String, Property> structure(T node) {

        Map<String, Property> properties = node.getChildNodes().stream()
                .filter(c -> !(c instanceof ChoiceSchemaNode)) // choices handled elsewhere
                .map(this::prop).collect(Collectors.toMap(pair -> pair.name, pair -> pair.property));

        Map<String, Property> choiceProperties = node.getChildNodes().stream()
                .filter(c -> (c instanceof ChoiceSchemaNode)) // handling choices
                .flatMap(c -> {
                    ChoiceSchemaNode choice = (ChoiceSchemaNode) c;
                    return choice.getCases().stream()
                            .flatMap(_case -> _case.getChildNodes().stream().map(sc -> {
                                Pair prop = prop(sc);
                                assignCaseMetadata(prop.property, choice, _case);
                                return prop;
                            }));
                }).collect(Collectors.toMap(pair -> pair.name, pair -> pair.property));;

        HashMap<String, Property> result = new HashMap<>();

        result.putAll(properties);
        result.putAll(choiceProperties);
        return result;
    }

    private static void assignCaseMetadata(Property property, ChoiceSchemaNode choice, ChoiceCaseNode aCase) {
        String choiceName = choice.getQName().getLocalName();
        String caseName = aCase.getQName().getLocalName();

        ((AbstractProperty) property).setVendorExtension("x-choice", choiceName + ":" + caseName);
    }

    private Property getPropertyByType(LeafListSchemaNode llN) {
        return converter.convert(llN.getType(), llN);
    }

    private Property getPropertyByType(LeafSchemaNode lN) {

        final Property property = converter.convert(lN.getType(), lN);
        property.setDefault(lN.getDefault());

        return property;
    }

    protected static String desc(DocumentedNode node) {
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
