package com.mrv.yangtools.codegen;

import org.opendaylight.yangtools.yang.model.api.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * @author bartosz.michalik@amartus.com
 */
public class DataNodeIterable implements Iterable<DataSchemaNode> {
    private List<DataSchemaNode> allChilds;

    public DataNodeIterable(final DataNodeContainer container) {
        allChilds = new LinkedList<>();
        traverse(container);
    }

    @Override
    public Iterator<DataSchemaNode> iterator() {
        return allChilds.iterator();
    }

    @Override
    public void forEach(Consumer<? super DataSchemaNode> action) {

    }

    @Override
    public Spliterator<DataSchemaNode> spliterator() {
        return allChilds.spliterator();
    }


    private void traverse(final DataNodeContainer dataNode) {
        if (dataNode == null) {
            return;
        }

        final Iterable<DataSchemaNode> childNodes = dataNode.getChildNodes();

        if (childNodes != null) {
            for (DataSchemaNode childNode : childNodes) {
                if (childNode.isAugmenting()) {
                    continue;
                }
                allChilds.add(childNode);
                if (childNode instanceof DataNodeContainer) {
                    final DataNodeContainer containerNode = (DataNodeContainer) childNode;
                    traverse(containerNode);
                } else if (childNode instanceof ChoiceSchemaNode) {
                    final ChoiceSchemaNode choiceNode = (ChoiceSchemaNode) childNode;
                    final Set<ChoiceCaseNode> cases = choiceNode.getCases();
                    if (cases != null) {
                        for (final ChoiceCaseNode caseNode : cases) {
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
            for (GroupingDefinition grouping : groupings) {
                traverse(grouping);
            }
        }
    }
}
