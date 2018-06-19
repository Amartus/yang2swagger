package com.mrv.yangtools.codegen.impl;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.util.EmptyConstraintDefinition;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchema;
import org.opendaylight.yangtools.yang.model.api.ConstraintDefinition;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.Status;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;
import org.opendaylight.yangtools.yang.model.api.UsesNode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class RpcContainerSchemaNode implements ContainerSchemaNode {

	private final RpcDefinition rpcDefinition;

    public RpcContainerSchemaNode(final RpcDefinition rpcDefinition) {
        this.rpcDefinition = rpcDefinition;
    }

    @Override
    public Set<GroupingDefinition> getGroupings() {
        return rpcDefinition.getGroupings();
    }

    @Override
    public Set<TypeDefinition<?>> getTypeDefinitions() {
        return rpcDefinition.getTypeDefinitions();
    }

    @Override
    public Set<AugmentationSchema> getAvailableAugmentations() {
        return ImmutableSet.of();
    }

    @Override
    public Collection<DataSchemaNode> getChildNodes() {
        final ContainerSchemaNode output = rpcDefinition.getOutput();
        if (output == null) {
            return ImmutableList.of();
        } else {
            return ImmutableList.of(output);
        }
    }

    @Override
    public DataSchemaNode getDataChildByName(QName name) {
        switch (name.getLocalName()) {
            case "input":
                return rpcDefinition.getInput();
            case "output":
                return rpcDefinition.getOutput();
            default:
                return null;
        }
    }

    @Override
    public boolean isAddedByUses() {
        return rpcDefinition.getOutput().isAddedByUses();
    }

    @Override
    public boolean isPresenceContainer() {
        return false;
    }

    @Override
    public Set<UsesNode> getUses() {
        return ImmutableSet.of();
    }

    @Override
    public boolean isAugmenting() {
        return false;
    }

    @Override
    public boolean isConfiguration() {
        return false;
    }

    @Override
    public ConstraintDefinition getConstraints() {
        return EmptyConstraintDefinition.create(false);
    }

    @Nonnull
    @Override
    public QName getQName() {
        return rpcDefinition.getQName();
    }

    @Nonnull
    @Override
    public SchemaPath getPath() {
        return rpcDefinition.getPath();
    }

    @Nullable
    @Override
    public String getDescription() {
        return rpcDefinition.getDescription();
    }

    @Nullable
    @Override
    public String getReference() {
        return rpcDefinition.getReference();
    }

    @Nonnull
    @Override
    public Status getStatus() {
        return rpcDefinition.getStatus();
    }

    @Nonnull
    @Override
    public List<UnknownSchemaNode> getUnknownSchemaNodes() {
        return ImmutableList.of();
    }
    
    @Override
    public boolean equals(Object obj) {
    	if(obj == null) return false;
    	if(obj instanceof RpcContainerSchemaNode) 
    		return ((RpcContainerSchemaNode)obj).rpcDefinition == this.rpcDefinition;
    	else 
    		return false;
    }
    
    @Override
    public int hashCode() {
    	return this.rpcDefinition.hashCode();
    }
}
