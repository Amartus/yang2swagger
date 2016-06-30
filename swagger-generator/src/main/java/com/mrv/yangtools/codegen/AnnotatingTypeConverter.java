package com.mrv.yangtools.codegen;

import io.swagger.models.properties.AbstractProperty;
import io.swagger.models.properties.Property;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;

/**
 * Annotate property with metadata for leafref
 * @author bartosz.michalik@amartus.com
 */
public class AnnotatingTypeConverter extends TypeConverter {
    public AnnotatingTypeConverter(SchemaContext ctx) {
        super(ctx);
    }

    @Override
    public Property convert(TypeDefinition<?> type, SchemaNode parent) {
        Property prop = super.convert(type, parent);

        if(prop instanceof AbstractProperty) {
            if(type instanceof LeafrefTypeDefinition) {
                String leafRef = ((LeafrefTypeDefinition) type).getPathStatement().toString();
                ((AbstractProperty) prop).setVendorExtension("x-path", leafRef);
            }
        }

        return prop;
    }
}
