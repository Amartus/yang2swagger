package com.mrv.yangtools.codegen.impl;

import io.swagger.models.*;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.model.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The builder strategy is to reuse grouping wherever possible. Thus composite models are to be use
 * @author bartosz.michalik@amartus.com
 */
public class OptimizingDataObjectBuilder extends AbstractDataObjectBuilder {
    private static final Logger log = LoggerFactory.getLogger(OptimizingDataObjectBuilder.class);

    private HashMap<SchemaPath, GroupingDefinition> groupings;
    private Map<SchemaNode, Model> existingModels;

    public OptimizingDataObjectBuilder(SchemaContext ctx, Swagger swagger) {
        super(ctx, swagger);
        groupings = new HashMap<>();
        existingModels = new HashMap<>();
    }


    @Override
    public String getName(SchemaNode node) {
        if(isGrouping(node)) {
            return names.get(grouping(node));
        }
        return names.get(node);
    }

    private GroupingDefinition grouping(SchemaNode node) {
        Set<UsesNode> uses = ((DataNodeContainer) node).getUses();
        assert uses.size() == 1;
        //noinspection SuspiciousMethodCalls
        GroupingDefinition grouping = groupings.get(uses.iterator().next().getGroupingPath());

       return grouping;
    }

    /**
     * Is node that has no attributes only single grouping
     * @param node
     * @return
     */
    private boolean isGrouping(SchemaNode node) {
        if(node instanceof DataNodeContainer) {
            Set<UsesNode> uses = ((DataNodeContainer) node).getUses();
            if(uses.size() == 1) {
                return ((DataNodeContainer) node).getChildNodes().stream()
                        .filter(n -> !n.isAddedByUses()).count() == 0;
            }
        }
        return false;
    }

    @Override
    protected void processNode(DataNodeContainer container, HashSet<String> cache) {
        super.processNode(container, cache);
        DataNodeHelper.stream(container).filter(n -> n instanceof GroupingDefinition)
                .forEach(n -> {
                    names.put(n, generateName(n, null, cache));
                    groupings.put(n.getPath(), (GroupingDefinition) n);
                });
    }

    @Override
    protected <T extends DataSchemaNode & DataNodeContainer> Property refOrStructure(T node) {
        final String definitionId = getDefinitionId(node);
        log.debug("reference to {}", definitionId);
        RefProperty prop = new RefProperty(definitionId);

        return prop;
    }




    @Override
    public <T extends SchemaNode & DataNodeContainer> Model build(T node) {

        Model model = existingModel(node);
        if(model != null) return model;

        model = model(node);
        if(model != null) return model;

        ComposedModel newModel = new ComposedModel();

        final ModelImpl attributes = new ModelImpl();
        attributes.description(desc(node));
        attributes.setProperties(structure(node, x->!x.isAddedByUses(), x->!x.isAddedByUses()));

        newModel.child(attributes);

        node.getUses().forEach(u -> {
            String groupingIdx = getDefinitionId(groupings.get(u.getGroupingPath()));
            log.debug("adding grouping {} to composed model", groupingIdx);
            newModel.child(new RefModel(groupingIdx));
            if(existingModel(node) == null) {
                log.debug("adding model {} for grouping", groupingIdx);
                addModel(groupings.get(u.getGroupingPath()));
            }
        });

        return newModel;
    }

    private <T extends SchemaNode & DataNodeContainer> Model existingModel(T node) {
        T toModel = isGrouping(node) ? (T) grouping(node) : node;
        return existingModels.get(toModel);
    }


    /**
     * Create model for a node with zero of single uses
     * @param node to process
     * @param <T> type
     * @return model or null in case more than one grouping is used
     */
    private <T extends SchemaNode & DataNodeContainer> Model model(T node) {

        if(node.getUses().size() > 1) return null;

        T toModel = isGrouping(node) ? (T) grouping(node) : node;

        final ModelImpl model = new ModelImpl();
        model.description(desc(toModel));
        model.setProperties(structure(toModel));

        existingModels.put(toModel, model);

        return model;
    }
}
