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

import com.mrv.yangtools.codegen.DataObjectBuilder;
import io.swagger.models.*;
import io.swagger.models.properties.*;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.mrv.yangtools.common.BindingMapping.getClassName;
import static com.mrv.yangtools.common.BindingMapping.nameToPackageSegment;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public abstract class AbstractDataObjectBuilder implements DataObjectBuilder {

    private static final Logger log = LoggerFactory.getLogger(AbstractDataObjectBuilder.class);

    protected static final String DEF_PREFIX = "#/definitions/";
    protected final Swagger swagger;
    protected final TypeConverter converter;
    protected final SchemaContext ctx;
    protected final ModuleUtils moduleUtils;
    protected final Map<SchemaNode, String> names;
    private final HashMap<QName, String> generatedEnums;
    private final HashMap<DataNodeContainer, String> orgNames;

    protected final static Function<DataNodeContainer, Set<AugmentationSchema>> augmentations = node -> {
        if(node instanceof AugmentationTarget) {
            Set<AugmentationSchema> res = ((AugmentationTarget) node).getAvailableAugmentations();
            if(res != null) return res;
        }
        return Collections.emptySet();
    };

    protected final static Predicate<DataNodeContainer> isAugmented = n -> !augmentations.apply(n).isEmpty();

    protected final Predicate<DataNodeContainer> isTreeAugmented = n ->  n != null && (isAugmented.test(n) || n.getChildNodes().stream()
            .filter(c -> c instanceof DataNodeContainer)
            .anyMatch(c -> this.isTreeAugmented.test((DataNodeContainer) c)));

    public AbstractDataObjectBuilder(SchemaContext ctx, Swagger swagger, TypeConverter converter) {
        this.names = new HashMap<>();
        this.converter = converter;
        converter.setDataObjectBuilder(this);
        this.swagger = swagger;
        this.ctx = ctx;
        this.moduleUtils = new ModuleUtils(ctx);
        this.generatedEnums = new HashMap<>();
        this.orgNames = new HashMap<>();
    }

    /**
     * Get definition id for node. Prerequisite is to have node's module traversed {@link UnpackingDataObjectsBuilder#processModule(Module)}.
     * @param node node
     * @return id
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer> String getDefinitionId(T node) {
        return DEF_PREFIX + getName(node);
    }

    /**
     * Traverse model to collect all verbs from YANG nodes that will constitute Swagger models
     * @param module to traverse
     */
    @Override
    public void processModule(Module module) {
        HashSet<String> cache = new HashSet<>(names.values());
        log.debug("processing data nodes defined in {}", module.getName());
        processNode(module, cache);

        log.debug("processing rpcs defined in {}", module.getName());
        module.getRpcs().forEach(r -> {
            if(r.getInput() != null)
                processNode(r.getInput(), null,  cache);
            if(r.getOutput() != null)
                processNode(new RpcContainerSchemaNode(r), null, cache);
        });
        log.debug("processing augmentations defined in {}", module.getName());
        module.getAugmentations().forEach(r -> processNode(r, cache));
    }

    protected  void processNode(ContainerSchemaNode container, String proposedName, Set<String> cache) {
        if(container == null) return;
        String name = generateName(container, null, cache);
        names.put(container, name);

        processNode(container, cache);
    }

    protected void processNode(DataNodeContainer container, Set<String> cache) {
        log.debug("DataNodeContainer string: {}", container.toString());
        DataNodeHelper.stream(container).filter(n -> n instanceof ContainerSchemaNode || n instanceof ListSchemaNode)
                .filter(n -> ! names.containsKey(n))
                .forEach(n -> {
                    String name = generateName(n, null, cache);
                    names.put(n, name);
                });
    }

    protected DataNodeContainer original(DataNodeContainer node) {
        DataNodeContainer result = null;
        DataNodeContainer tmp = node;
        do {
            if(tmp instanceof DerivableSchemaNode) {
                com.google.common.base.Optional<? extends SchemaNode> original = ((DerivableSchemaNode) tmp).getOriginal();
                tmp = null;
                if(original.isPresent() && original.get() instanceof DataNodeContainer) {
                    result = (DataNodeContainer) original.get();
                    tmp = result;
                }
            } else {
                tmp = null;
            }
        } while (tmp != null);

        return result;
    }

    protected String generateName(SchemaNode node, String proposedName, Set<String> _cache) {
        if(node instanceof DataNodeContainer) {
            DataNodeContainer original = null;
            if(! isTreeAugmented.test((DataNodeContainer) node)) {
                original = original((DataNodeContainer) node);
            }

            if(original != null) {
                if(! orgNames.containsKey(original)) {
                    String name = generateName((SchemaNode)original, proposedName, _cache);
                    orgNames.put(original, name);
                } else {
                    log.debug("reusing original definition to get name for {}", node.getQName());
                }

                return orgNames.get(original);
            } else {
                DataNodeContainer t = (DataNodeContainer) node;
                if(orgNames.containsKey(t)) {
                    return orgNames.get(t);
                }
            }
        }

        String modulePrefix =  nameToPackageSegment(moduleUtils.toModuleName(node.getQName()));
        if(proposedName != null) {
            return modulePrefix + "." + getClassName(proposedName);
        }

        String name = getClassName(node.getQName());
        final Iterable<QName> path = node.getPath().getParent().getPathFromRoot();
        if(path == null || !path.iterator().hasNext()) {
            log.debug("generatedName: {}", modulePrefix + "." + name);
            return modulePrefix + "." + name;
        }
        String pkg = StreamSupport.stream(path.spliterator(), false).map(n -> getClassName(n.getLocalName()).toLowerCase()).collect(Collectors.joining("."));
        log.debug("generatedName: {}", modulePrefix + "." + pkg + "." + name);
        return modulePrefix + "." + pkg + "." + name;
    }

    /**
     * Convert leaf-list to swagger property
     * @param llN leaf-list
     * @return property
     */
    protected Property getPropertyByType(LeafListSchemaNode llN) {
        return converter.convert(llN.getType(), llN);
    }

    /**
     * Convert leaf to swagger property
     * @param lN leaf
     * @return property
     */
    protected Property getPropertyByType(LeafSchemaNode lN) {

        final Property property = converter.convert(lN.getType(), lN);
        property.setDefault(lN.getDefault());

        return property;
    }


    protected Map<String, Property> structure(DataNodeContainer node) {
        return structure(node,  x -> true, x -> true);
    }


    protected Map<String, Property> structure(DataNodeContainer node, Predicate<DataSchemaNode> acceptNode, Predicate<DataSchemaNode> acceptChoice) {

        Predicate<DataSchemaNode> choiceP = c -> c instanceof ChoiceSchemaNode;

        // due to how inheritance is handled in yangtools the localName node collisions might appear
        // thus we need to apply collision strategy to override with the last attribute available
        Map<String, Property> properties = node.getChildNodes().stream()
                .filter(choiceP.negate().and(acceptNode)) // choices handled elsewhere
                .map(this::prop).collect(Collectors.toMap(pair -> pair.name, pair -> pair.property, (oldV, newV) -> newV));

        Map<String, Property> choiceProperties = node.getChildNodes().stream()
                .filter(choiceP.and(acceptChoice)) // handling choices
                .flatMap(c -> {
                    ChoiceSchemaNode choice = (ChoiceSchemaNode) c;
                    Stream<Pair> streamOfPairs = choice.getCases().stream()
                            .flatMap(_case -> _case.getChildNodes().stream().map(sc -> {
                                Pair prop = prop(sc);
                                assignCaseMetadata(prop.property, choice, _case);
                                return prop;
                            }));
                    return streamOfPairs;
                }).collect(Collectors.toMap(pair -> pair.name, pair -> pair.property, (oldV, newV) -> newV));

        HashMap<String, Property> result = new HashMap<>();

        result.putAll(properties);
        result.putAll(choiceProperties);
        return result;
    }

    protected Pair prop(DataSchemaNode node) {
        final String propertyName = getPropertyName(node);

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
        } else if (node instanceof AnyXmlSchemaNode) {
            log.warn("generating swagger string property for any schema type for {}", node.getQName());
            prop = new StringProperty();
        }

        if (prop != null) {
            prop.setDescription(desc(node));
        }

        return new Pair(propertyName, prop);
    }

    public String getPropertyName(DataSchemaNode node) {
        //return BindingMapping.getPropertyName(node.getQName().getLocalName());
        String name = node.getQName().getLocalName();
        if(node.isAugmenting()) {
            name = moduleName(node) + ":" + name;
        }
        return name;
    }

    private String moduleName(DataSchemaNode node) {
        Module module = ctx.findModuleByNamespaceAndRevision(node.getQName().getNamespace(), node.getQName().getRevision());
        return module.getName();
    }

    protected abstract <T extends DataSchemaNode & DataNodeContainer> Property refOrStructure(T node);

    private static void assignCaseMetadata(Property property, ChoiceSchemaNode choice, ChoiceCaseNode aCase) {
        String choiceName = choice.getQName().getLocalName();
        String caseName = aCase.getQName().getLocalName();

        ((AbstractProperty) property).setVendorExtension("x-choice", choiceName + ":" + caseName);
    }

    /**
     * Add model to referenced swagger for given node. All related models are added as well if needed.
     * @param node for which build a node
     * @param modelName model name
     * @param <T> type of the node
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer> void addModel(T node, String modelName) {


        Model model = build(node);


        if(swagger.getDefinitions() != null && swagger.getDefinitions().containsKey(modelName)) {
            if(model.equals(swagger.getDefinitions().get(modelName))) {
                return;
            }
            log.warn("Overriding model {} with node {}", modelName, node.getQName());
        }

        swagger.addDefinition(modelName, model);
    }

    public <T extends SchemaNode & DataNodeContainer> void addModel(T node) {
        addModel(node, getName(node));
    }


    @Override
    public String addModel(EnumTypeDefinition enumType) {
        QName qName = enumType.getQName();
        
        //inline enumerations are a special case that needs extra enumeration
        if(qName.getLocalName().equals("enumeration") && enumType.getBaseType() == null) {
        	qName = QName.create(qName, enumType.getPath().getParent().getLastComponent().getLocalName() + "-" + qName.getLocalName());
        }

        if(! generatedEnums.containsKey(qName)) {
            log.debug("generating enum model for {}",  qName);
            String name = getName(qName);
            ModelImpl enumModel = build(enumType, qName);
            swagger.addDefinition(name, enumModel);
            generatedEnums.put(qName, DEF_PREFIX + name);
        } else {
            log.debug("reusing enum model for {}", enumType.getQName());
        }
        return generatedEnums.get(qName);
    }

    protected ModelImpl build(EnumTypeDefinition enumType, QName qName) {
        ModelImpl model = new ModelImpl();
        model.setEnum(enumType.getValues().stream()
                .map(EnumTypeDefinition.EnumPair::getName).collect(Collectors.toList()));
        model.setType("string");
        model.setReference(getName(qName));
        return model;
    }

    protected String getName(QName qname) {
        String modulePrefix =  nameToPackageSegment(moduleUtils.toModuleName(qname));
        String name = modulePrefix + "." + getClassName(qname);

        String candidate = name;

        int idx = 1;
        while(generatedEnums.values().contains(DEF_PREFIX + candidate)) {
            log.warn("Name {} already defined for enum. generating random postfix", candidate);
            candidate = name + idx;
        }
        return candidate;
    }

    protected String desc(DocumentedNode node) {
        return  node.getReference() == null ? node.getDescription() :
                node.getDescription() + " REF:" + node.getReference();
    }

    protected static class Pair {
        final protected String name;
        final protected Property property;

        protected Pair(String name, Property property) {
            this.name = name;
            this.property = property;
        }
    }
}
