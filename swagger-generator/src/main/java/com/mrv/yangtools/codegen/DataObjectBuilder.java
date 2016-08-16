package com.mrv.yangtools.codegen;

import io.swagger.models.Model;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * @author bartosz.michalik@amartus.com
 */
public interface DataObjectBuilder extends DataObjectRepo {
    /**
     * Build model for a given node
     * @param node for which model is to be build
     * @param <T> composed type
     * @return model for node
     */
    <T extends SchemaNode & DataNodeContainer> Model build(T node);

    void processModule(Module module);

    <T extends SchemaNode & DataNodeContainer> void addModel(T node);
}
