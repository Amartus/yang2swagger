package com.mrv.yangtools.codegen;

import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;

/**
 * @author bartosz.michalik@amartus.com
 */
public interface DataObjectRepo {
    /**
     * Get definition id for node. Prerequisite is to have node's module traversed {@link DataObjectsBuilder#processModule(Module)}.
     * @param node node
     * @return id
     */
    String getDefinitionId(DataSchemaNode node);
    /**
     * Get name for data node. Prerequisite is to have node's module traversed {@link DataObjectsBuilder#processModule(Module)}.
     * @param node node
     * @return name
     */
    String getName(DataSchemaNode node);

}
