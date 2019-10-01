package com.mrv.yangtools.codegen.impl.postprocessor;

import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.RefProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Wrap payload with a single rooted, works with {@link com.mrv.yangtools.codegen.impl.path.rfc8040.RestconfPathPrinter}
 * path schema only
 * @author bartosz.michalik@amartus.com
 *
 */
public abstract class PayloadWrapperProcessor implements Consumer<Swagger> {
    private static final Logger log = LoggerFactory.getLogger(PayloadWrapperProcessor.class);
    private static final String POSTFIX = "Wrapper";
    private Swagger swagger;

    @Override
    public void accept(Swagger swagger) {
        this.swagger = Objects.requireNonNull(swagger);
        this.swagger.getPaths().entrySet()
                .forEach(e -> processPath(e.getKey(), e.getValue()));
    }

    private void processPath(String key, Path path) {
        if(key.startsWith("/operations")) {
            return;
        }

        processOperation(path.getGet(), toProperty(key));
        processOperation(path.getPut(), toProperty(key));
        processOperation(path.getPost(), toProperty(key));
        processOperation(path.getDelete(), toProperty(key));
    }

    protected abstract String toProperty(String path);

    private void processOperation(Operation operation, String propertyName) {
        if(operation == null) {
            return;
        }

        operation.getResponses().values().stream()
                .filter(r -> r.getSchema() instanceof RefProperty)
                .forEach(r -> wrap(propertyName, r));

        operation.getParameters().stream()
                .filter(p -> p instanceof BodyParameter)
                .map(p -> (BodyParameter) p)
                .filter(p -> p.getSchema() instanceof RefModel)
                .forEach(param -> wrap(propertyName, param));


    }

    private void wrap(String propertyName, Response r) {
        RefProperty prop = (RefProperty) r.getSchema();
        String wrapperName = wrap(propertyName, prop.getSimpleRef());
        r.setSchema(new RefProperty(wrapperName));
    }

    private void wrap(String propertyName, BodyParameter param) {
        RefModel m = (RefModel) param.getSchema();
        String wrapperName = wrap(propertyName, m.getSimpleRef());
        param.setSchema(new RefModel(wrapperName));
    }

    private String wrap(String propertyName, String simpleRef) {
        String wrapperName = simpleRef + POSTFIX;
        Model model = swagger.getDefinitions().get(wrapperName);
        if (model == null) {
            addWrappingModel(wrapperName, propertyName, simpleRef);
        }
        return wrapperName;
    }

    private void addWrappingModel(String wrapperName, String propertyName, String simpleRef) {
        log.info("Adding top-level model {} {} -> {}", wrapperName, propertyName, simpleRef);
        ModelImpl model = new ModelImpl();
        model.addProperty(propertyName, new RefProperty(simpleRef));
        swagger.addDefinition(wrapperName, model);
    }
}
