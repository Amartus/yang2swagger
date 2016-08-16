package com.mrv.yangtools.codegen;

import com.mrv.yangtools.codegen.impl.UnpackingDataObjectsBuilder;
import io.swagger.models.Model;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * @author bartosz.michalik@amartus.com
 */
public interface DataObjectRepo {
    /**
     * Get definition id for node. Prerequisite is to have node's module traversed {@link UnpackingDataObjectsBuilder#processModule(Module)}.
     * @param node node
     * @return id
     */
    String getDefinitionId(SchemaNode node);
    /**
     * Get name for data node. Prerequisite is to have node's module traversed {@link UnpackingDataObjectsBuilder#processModule(Module)}.
     * @param node node
     * @return name
     */
    String getName(SchemaNode node);
}
